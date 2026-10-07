package com.edstem.app.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edstem.app.product.dto.request.ProductRequest;
import com.edstem.app.product.dto.response.ProductResponse;
import com.edstem.app.product.exception.ProductNotFoundException;
import com.edstem.app.product.service.ProductService;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

/**
 * Shows how we know the cache works: Hibernate counts the SQL statements it runs. A repeated lookup
 * adds none, and a lookup after an update or delete adds one and returns the new data.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProductCacheTest {

  @Autowired private ProductService productService;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private CacheManager cacheManager;

  private Statistics statistics;

  @BeforeEach
  void setUp() {
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    cacheManager.getCache(ProductService.CACHE_NAME).clear();
  }

  @Test
  void repeatedLookupsOfTheSameProductReadTheDatabaseOnlyOnce() {
    UUID id = newProduct("Kettle", 5);
    statistics.clear();

    for (int i = 0; i < 20; i++) {
      assertThat(productService.get(id).name()).isEqualTo("Kettle");
    }

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    assertThat(statistics.getEntityLoadCount()).isEqualTo(1);
  }

  @Test
  void eachProductHasItsOwnCacheEntry() {
    UUID first = newProduct("First", 1);
    UUID second = newProduct("Second", 2);
    statistics.clear();

    for (int i = 0; i < 5; i++) {
      assertThat(productService.get(first).name()).isEqualTo("First");
      assertThat(productService.get(second).name()).isEqualTo("Second");
    }

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  void aLookupAfterAnUpdateReturnsTheNewDataAndReadsTheDatabaseAgain() {
    UUID id = newProduct("Kettle", 5);
    assertThat(productService.get(id).stock()).isEqualTo(5);
    statistics.clear();

    productService.update(id, new ProductRequest("Kettle Pro", "Home", 3000L, 9, 4.9));
    statistics.clear();
    ProductResponse afterUpdate = productService.get(id);
    productService.get(id);
    productService.get(id);

    assertThat(afterUpdate.name()).isEqualTo("Kettle Pro");
    assertThat(afterUpdate.priceCents()).isEqualTo(3000);
    assertThat(afterUpdate.stock()).isEqualTo(9);
    assertThat(statistics.getPrepareStatementCount())
        .as("one read for the first lookup after the update, none for the next two")
        .isEqualTo(1);
  }

  @Test
  void aLookupAfterADeleteFailsInsteadOfReturningTheOldProduct() {
    UUID id = newProduct("Kettle", 5);
    assertThat(productService.get(id)).isNotNull();

    productService.delete(id);

    assertThatThrownBy(() -> productService.get(id)).isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void aProductThatDoesNotExistIsNotCached() {
    UUID unknown = UUID.randomUUID();
    statistics.clear();

    assertThatThrownBy(() -> productService.get(unknown))
        .isInstanceOf(ProductNotFoundException.class);
    assertThatThrownBy(() -> productService.get(unknown))
        .isInstanceOf(ProductNotFoundException.class);

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  void aProductCreatedAfterAMissedLookupCanBeFound() {
    UUID id = newProduct("Kettle", 5);
    productService.delete(id);
    assertThatThrownBy(() -> productService.get(id)).isInstanceOf(ProductNotFoundException.class);

    UUID another = newProduct("Lamp", 3);

    assertThat(productService.get(another).name()).isEqualTo("Lamp");
  }

  @Test
  void readersNeverSeeDataOlderThanTheLatestFinishedUpdate() throws Exception {
    UUID id = newProduct("Busy", 0);
    AtomicInteger finishedUpdate = new AtomicInteger(0);
    AtomicBoolean updatesDone = new AtomicBoolean(false);
    List<String> staleReads = new CopyOnWriteArrayList<>();
    ExecutorService readers = Executors.newFixedThreadPool(4);
    List<Future<?>> running = new ArrayList<>();
    try {
      for (int i = 0; i < 4; i++) {
        running.add(
            readers.submit(
                () -> {
                  while (!updatesDone.get()) {
                    int atLeast = finishedUpdate.get();
                    int seen = productService.get(id).stock();
                    if (seen < atLeast) {
                      staleReads.add("read " + seen + " after update " + atLeast + " finished");
                    }
                  }
                }));
      }

      for (int stock = 1; stock <= 300; stock++) {
        productService.update(id, new ProductRequest("Busy", "Home", 100L, stock, 4.0));
        finishedUpdate.set(stock);
      }
      updatesDone.set(true);
      for (Future<?> reader : running) {
        reader.get();
      }
    } finally {
      readers.shutdownNow();
    }

    assertThat(staleReads).isEmpty();
    assertThat(productService.get(id).stock()).isEqualTo(300);
  }

  private UUID newProduct(String name, int stock) {
    return productService.create(new ProductRequest(name, "Home", 1000L, stock, 4.0)).id();
  }
}
