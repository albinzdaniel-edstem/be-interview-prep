package com.edstem.app.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edstem.app.product.dto.request.ProductFilter;
import com.edstem.app.product.dto.request.ProductRequest;
import com.edstem.app.product.dto.response.ProductResponse;
import com.edstem.app.product.entity.Product;
import com.edstem.app.product.exception.InvalidPriceRangeException;
import com.edstem.app.product.exception.ProductNotFoundException;
import com.edstem.app.product.mapper.ProductMapper;
import com.edstem.app.product.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;

  private final ConcurrentMapCacheManager cacheManager =
      new ConcurrentMapCacheManager(ProductService.CACHE_NAME);
  private ProductService productService;

  @BeforeEach
  void setUp() {
    productService = new ProductService(productRepository, new ProductMapper(), cacheManager);
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void createStoresTheProductAndTrimsText() {
    ProductRequest request = new ProductRequest("  Blue Kettle ", " Home ", 2500L, 5, 4.5);
    when(productRepository.saveAndFlush(any(Product.class)))
        .thenAnswer(call -> call.getArgument(0));

    ProductResponse response = productService.create(request);

    assertThat(response.name()).isEqualTo("Blue Kettle");
    assertThat(response.category()).isEqualTo("Home");
    assertThat(response.priceCents()).isEqualTo(2500);
    assertThat(response.stock()).isEqualTo(5);
    assertThat(response.rating()).isEqualTo(4.5);
  }

  @Test
  void listAddsTheIdAsTheLastSortKeySoPagesDoNotOverlap() {
    Pageable requested = PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "rating"));
    when(productRepository.findAll(anySpecification(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    productService.list(ProductFilter.none(), requested);

    Pageable used = capturedPageable();
    assertThat(used.getPageNumber()).isEqualTo(2);
    assertThat(used.getPageSize()).isEqualTo(10);
    assertThat(used.getSort().toList())
        .extracting(order -> order.getProperty() + ":" + order.getDirection())
        .containsExactly("rating:DESC", "id:ASC");
  }

  @Test
  void listSortsByIdWhenNoSortIsGiven() {
    when(productRepository.findAll(anySpecification(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    productService.list(ProductFilter.none(), PageRequest.of(0, 20));

    assertThat(capturedPageable().getSort().toList())
        .extracting(Sort.Order::getProperty)
        .containsExactly("id");
  }

  @Test
  void listKeepsAnIdSortThatTheCallerAskedFor() {
    when(productRepository.findAll(anySpecification(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    productService.list(
        ProductFilter.none(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));

    assertThat(capturedPageable().getSort().toList())
        .extracting(order -> order.getProperty() + ":" + order.getDirection())
        .containsExactly("id:DESC");
  }

  @Test
  void listReturnsTheTotalsFromTheRepository() {
    Pageable pageable = PageRequest.of(0, 2);
    Page<Product> page = new PageImpl<>(List.of(product(), product()), pageable, 5);
    when(productRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(page);

    Page<ProductResponse> result = productService.list(ProductFilter.none(), pageable);

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(5);
    assertThat(result.getTotalPages()).isEqualTo(3);
  }

  @Test
  void listRejectsAMinimumPriceAboveTheMaximum() {
    ProductFilter filter = new ProductFilter(null, 5000L, 1000L, null, null);

    assertThatThrownBy(() -> productService.list(filter, PageRequest.of(0, 20)))
        .isInstanceOf(InvalidPriceRangeException.class)
        .hasMessage("minPriceCents must not be greater than maxPriceCents");
    verify(productRepository, never()).findAll(anySpecification(), any(Pageable.class));
  }

  @Test
  void listAcceptsEqualMinimumAndMaximumPrices() {
    ProductFilter filter = new ProductFilter(null, 1000L, 1000L, null, null);
    when(productRepository.findAll(anySpecification(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    Page<ProductResponse> result = productService.list(filter, PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  void getReturnsTheProduct() {
    Product product = product();
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

    ProductResponse response = productService.get(product.getId());

    assertThat(response.id()).isEqualTo(product.getId());
  }

  @Test
  void getThrowsWhenTheProductDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(productRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.get(id)).isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void updateReplacesEveryField() {
    Product product = product();
    ProductRequest request = new ProductRequest(" New name ", "Kitchen", 999L, 0, 3.5);
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);

    ProductResponse response = productService.update(product.getId(), request);

    assertThat(response.name()).isEqualTo("New name");
    assertThat(response.category()).isEqualTo("Kitchen");
    assertThat(response.priceCents()).isEqualTo(999);
    assertThat(response.stock()).isZero();
    assertThat(response.rating()).isEqualTo(3.5);
  }

  @Test
  void updateThrowsWhenTheProductDoesNotExist() {
    UUID id = UUID.randomUUID();
    ProductRequest request = new ProductRequest("A", "B", 1L, 1, 1.0);
    when(productRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.update(id, request))
        .isInstanceOf(ProductNotFoundException.class);
    verify(productRepository, never()).saveAndFlush(any(Product.class));
  }

  @Test
  void deleteRemovesTheProduct() {
    Product product = product();
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

    productService.delete(product.getId());

    verify(productRepository).delete(product);
  }

  @Test
  void deleteThrowsWhenTheProductDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(productRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.delete(id))
        .isInstanceOf(ProductNotFoundException.class);
    verify(productRepository, never()).delete(any(Product.class));
  }

  @Test
  void updateRemovesTheCachedProductOnlyAfterTheCommit() {
    Product product = product();
    Cache cache = cachedEntryFor(product.getId());
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);

    productService.update(product.getId(), new ProductRequest("A", "B", 1L, 1, 1.0));

    assertThat(cache.get(product.getId())).as("still cached before the commit").isNotNull();
    commit();
    assertThat(cache.get(product.getId())).as("removed after the commit").isNull();
  }

  @Test
  void deleteRemovesTheCachedProductOnlyAfterTheCommit() {
    Product product = product();
    Cache cache = cachedEntryFor(product.getId());
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

    productService.delete(product.getId());

    assertThat(cache.get(product.getId())).as("still cached before the commit").isNotNull();
    commit();
    assertThat(cache.get(product.getId())).as("removed after the commit").isNull();
  }

  @Test
  void aRolledBackUpdateLeavesTheCachedProductAlone() {
    Product product = product();
    Cache cache = cachedEntryFor(product.getId());
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);

    productService.update(product.getId(), new ProductRequest("A", "B", 1L, 1, 1.0));
    rollBack();

    assertThat(cache.get(product.getId())).isNotNull();
  }

  @Test
  void aFailedUpdateOfAnUnknownProductDoesNotTouchOtherCachedProducts() {
    Product cached = product();
    Cache cache = cachedEntryFor(cached.getId());
    UUID unknown = UUID.randomUUID();
    when(productRepository.findById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> productService.update(unknown, new ProductRequest("A", "B", 1L, 1, 1.0)))
        .isInstanceOf(ProductNotFoundException.class);
    commit();

    assertThat(cache.get(cached.getId())).isNotNull();
  }

  @Test
  void theCachedProductIsRemovedAtOnceWhenThereIsNoTransaction() {
    TransactionSynchronizationManager.clearSynchronization();
    Product product = product();
    Cache cache = cachedEntryFor(product.getId());
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

    productService.delete(product.getId());

    assertThat(cache.get(product.getId())).isNull();
  }

  private Cache cachedEntryFor(UUID id) {
    Cache cache = cacheManager.getCache(ProductService.CACHE_NAME);
    cache.put(id, "stale value");
    return cache;
  }

  private static void commit() {
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
  }

  private static void rollBack() {
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
  }

  private Pageable capturedPageable() {
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(productRepository).findAll(anySpecification(), pageable.capture());
    return pageable.getValue();
  }

  @SuppressWarnings("unchecked")
  private static Specification<Product> anySpecification() {
    return any(Specification.class);
  }

  private static Product product() {
    return Product.builder()
        .id(UUID.randomUUID())
        .name("Blue Kettle")
        .category("Home")
        .priceCents(2500)
        .stock(5)
        .rating(4.5)
        .build();
  }
}
