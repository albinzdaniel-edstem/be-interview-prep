package com.edstem.app.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edstem.app.product.dto.request.ProductFilter;
import com.edstem.app.product.entity.Product;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@DataJpaTest
class ProductRepositoryTest {

  private static final Pageable ALL = PageRequest.of(0, 50, Sort.by("name"));

  @Autowired private ProductRepository productRepository;

  @BeforeEach
  void setUp() {
    productRepository.saveAll(
        List.of(
            product("Blue Kettle", "Home", 2500, 5, 4.5),
            product("Red Kettle", "Home", 4000, 0, 3.0),
            product("Blue Lamp", "Home", 1500, 10, 4.0),
            product("Green Pan", "Kitchen", 3000, 3, 4.8),
            product("100% Cotton Mat", "Sports", 2000, 7, 4.1),
            product("Snake_Case Book", "Books", 1000, 0, 2.0)));
  }

  @Test
  void noFilterReturnsEveryProduct() {
    Page<Product> result = search(ProductFilter.none());

    assertThat(result.getTotalElements()).isEqualTo(6);
  }

  @Test
  void filtersByCategoryIgnoringCase() {
    Page<Product> result = search(new ProductFilter("hOmE", null, null, null, null));

    assertThat(names(result)).containsExactly("Blue Kettle", "Blue Lamp", "Red Kettle");
  }

  @Test
  void priceRangeIncludesBothEnds() {
    Page<Product> atLeast = search(new ProductFilter(null, 2500L, null, null, null));
    Page<Product> atMost = search(new ProductFilter(null, null, 2000L, null, null));
    Page<Product> between = search(new ProductFilter(null, 2000L, 3000L, null, null));

    assertThat(names(atLeast)).containsExactly("Blue Kettle", "Green Pan", "Red Kettle");
    assertThat(names(atMost)).containsExactly("100% Cotton Mat", "Blue Lamp", "Snake_Case Book");
    assertThat(names(between)).containsExactly("100% Cotton Mat", "Blue Kettle", "Green Pan");
  }

  @Test
  void inStockOnlyHidesProductsWithNoStock() {
    Page<Product> inStock = search(new ProductFilter(null, null, null, true, null));
    Page<Product> notRestricted = search(new ProductFilter(null, null, null, false, null));

    assertThat(names(inStock))
        .containsExactly("100% Cotton Mat", "Blue Kettle", "Blue Lamp", "Green Pan");
    assertThat(notRestricted.getTotalElements()).isEqualTo(6);
  }

  @Test
  void nameSearchFindsPartsOfANameIgnoringCase() {
    Page<Product> kettles = search(new ProductFilter(null, null, null, null, "kETtle"));
    Page<Product> blue = search(new ProductFilter(null, null, null, null, " BLUE "));

    assertThat(names(kettles)).containsExactly("Blue Kettle", "Red Kettle");
    assertThat(names(blue)).containsExactly("Blue Kettle", "Blue Lamp");
  }

  @Test
  void percentAndUnderscoreInTheSearchMatchThemselvesNotAnything() {
    Page<Product> percent = search(new ProductFilter(null, null, null, null, "%"));
    Page<Product> underscore = search(new ProductFilter(null, null, null, null, "_"));

    assertThat(names(percent)).containsExactly("100% Cotton Mat");
    assertThat(names(underscore)).containsExactly("Snake_Case Book");
  }

  @Test
  void everyFilterTogetherWorksInOneRequest() {
    ProductFilter filter = new ProductFilter("Home", 1000L, 3000L, true, "blue");

    Page<Product> result = search(filter);

    assertThat(names(result)).containsExactly("Blue Kettle", "Blue Lamp");
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void filtersThatExcludeEverythingGiveAnEmptyPage() {
    ProductFilter filter = new ProductFilter("Home", 4001L, null, true, null);

    Page<Product> result = search(filter);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.getTotalPages()).isZero();
  }

  @Test
  void blankCategoryAndSearchTextAreIgnored() {
    Page<Product> result = search(new ProductFilter("  ", null, null, null, "  "));

    assertThat(result.getTotalElements()).isEqualTo(6);
  }

  @Test
  void reportsTheTotalCountAndNumberOfPages() {
    Page<Product> secondPage =
        productRepository.findAll(
            ProductSpecifications.from(ProductFilter.none()),
            PageRequest.of(1, 4, Sort.by("name")));

    assertThat(secondPage.getContent()).hasSize(2);
    assertThat(secondPage.getTotalElements()).isEqualTo(6);
    assertThat(secondPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  void pagesAreCountedOnTheFilteredResult() {
    Pageable pageable = PageRequest.of(0, 2, Sort.by("name"));

    Page<Product> result =
        productRepository.findAll(
            ProductSpecifications.from(new ProductFilter("Home", null, null, null, null)),
            pageable);

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(3);
    assertThat(result.getTotalPages()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", "category", "priceCents", "stock", "rating", "createdAt"})
  void sortsByEveryProductField(String field) {
    Pageable ascending = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, field, "id"));
    Pageable descending = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, field, "id"));

    Page<Product> up =
        productRepository.findAll(ProductSpecifications.from(ProductFilter.none()), ascending);
    Page<Product> down =
        productRepository.findAll(ProductSpecifications.from(ProductFilter.none()), descending);

    assertThat(up.getContent()).hasSize(6);
    assertThat(down.getContent()).hasSize(6);
  }

  @Test
  void sortingByPriceOrdersTheCheapestFirst() {
    Pageable pageable = PageRequest.of(0, 50, Sort.by("priceCents"));

    Page<Product> result =
        productRepository.findAll(ProductSpecifications.from(ProductFilter.none()), pageable);

    assertThat(result.getContent())
        .extracting(Product::getPriceCents)
        .containsExactly(1000L, 1500L, 2000L, 2500L, 3000L, 4000L);
  }

  private Page<Product> search(ProductFilter filter) {
    return productRepository.findAll(ProductSpecifications.from(filter), ALL);
  }

  private static List<String> names(Page<Product> page) {
    return page.getContent().stream().map(Product::getName).toList();
  }

  private static Product product(
      String name, String category, long priceCents, int stock, double rating) {
    return Product.builder()
        .name(name)
        .category(category)
        .priceCents(priceCents)
        .stock(stock)
        .rating(rating)
        .build();
  }
}
