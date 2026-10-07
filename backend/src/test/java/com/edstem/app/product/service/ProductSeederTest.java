package com.edstem.app.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edstem.app.product.entity.Product;
import com.edstem.app.product.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSeederTest {

  @Mock private ProductRepository productRepository;
  @InjectMocks private ProductSeeder seeder;

  @Test
  void seedsOneHundredProductsWhenTheTableIsEmpty() {
    when(productRepository.count()).thenReturn(0L);

    seeder.run(null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Product>> saved = ArgumentCaptor.forClass(List.class);
    verify(productRepository).saveAll(saved.capture());
    assertThat(saved.getValue()).hasSize(100);
  }

  @Test
  void doesNotSeedAgainWhenProductsExist() {
    when(productRepository.count()).thenReturn(7L);

    seeder.run(null);

    verify(productRepository, never()).saveAll(anyList());
  }

  @Test
  void generatedProductsAreValidAndVaried() {
    List<Product> products = ProductSeeder.generate(100);

    assertThat(products).extracting(Product::getName).doesNotHaveDuplicates();
    assertThat(products)
        .extracting(Product::getCategory)
        .doesNotContainNull()
        .containsAnyOf("Home", "Books");
    assertThat(products.stream().map(Product::getCategory).distinct().count()).isGreaterThan(4);
    assertThat(products)
        .allSatisfy(
            product -> {
              assertThat(product.getPriceCents()).isBetween(199L, 50_000L);
              assertThat(product.getStock()).isBetween(0, 200);
              assertThat(product.getRating()).isBetween(1.0, 5.0);
            });
    assertThat(products).anyMatch(product -> product.getStock() == 0);
    assertThat(products).anyMatch(product -> product.getStock() > 0);
  }

  @Test
  void generatesTheSameDataOnEveryRun() {
    List<Product> first = ProductSeeder.generate(100);
    List<Product> second = ProductSeeder.generate(100);

    assertThat(first).usingRecursiveComparison().isEqualTo(second);
  }
}
