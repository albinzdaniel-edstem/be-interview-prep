package com.edstem.app.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.app.product.entity.Product;
import com.edstem.app.product.repository.ProductRepository;
import com.edstem.app.product.service.ProductSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.catalog.seed.enabled=true")
@AutoConfigureMockMvc
class ProductApiIntegrationTest {

  private static final String URL = "/api/v1/products";
  private static final String PASSWORD = UUID.randomUUID().toString();

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProductRepository productRepository;

  private final List<Product> seeded = ProductSeeder.generate(ProductSeeder.SEED_COUNT);
  private String token;

  @BeforeEach
  void logIn() throws Exception {
    String email = "shopper-" + UUID.randomUUID() + "@example.com";
    String credentials = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials))
        .andExpect(status().isCreated());
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials))
            .andReturn()
            .getResponse()
            .getContentAsString();
    token = objectMapper.readTree(body).at("/data/accessToken").asText();
  }

  @Test
  void theCatalogNeedsALogin() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }

  @Test
  void theHundredSeededProductsAreListedWithTotals() throws Exception {
    mvc.perform(authorized(get(URL).param("size", "100")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(100))
        .andExpect(jsonPath("$.data.totalElements").value(100))
        .andExpect(jsonPath("$.data.totalPages").value(1));
  }

  @Test
  void thePageSizeIsCappedAt100() throws Exception {
    mvc.perform(authorized(get(URL).param("size", "5000")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.size").value(100))
        .andExpect(jsonPath("$.data.content.length()").value(100));
  }

  @Test
  void theTotalCountAndNumberOfPagesFollowThePageSize() throws Exception {
    mvc.perform(authorized(get(URL).param("size", "30")))
        .andExpect(jsonPath("$.data.totalElements").value(100))
        .andExpect(jsonPath("$.data.totalPages").value(4))
        .andExpect(jsonPath("$.data.content.length()").value(30));
    mvc.perform(authorized(get(URL).param("size", "30").param("page", "3")))
        .andExpect(jsonPath("$.data.content.length()").value(10));
    mvc.perform(authorized(get(URL).param("size", "30").param("page", "4")))
        .andExpect(jsonPath("$.data.content.length()").value(0));
  }

  @Test
  void everyCombinationOfFiltersWorksInOneRequestAndMatchesAnIndependentCount() throws Exception {
    Predicate<Product> home = p -> p.getCategory().equalsIgnoreCase("home");
    Predicate<Product> midPrice = p -> p.getPriceCents() >= 5_000 && p.getPriceCents() <= 30_000;
    Predicate<Product> inStock = p -> p.getStock() > 0;
    Predicate<Product> nameHasLamp = p -> p.getName().toLowerCase(Locale.ROOT).contains("lamp");

    assertFilters(List.of("category=home"), home);
    assertFilters(List.of("minPriceCents=5000", "maxPriceCents=30000"), midPrice);
    assertFilters(List.of("inStock=true"), inStock);
    assertFilters(List.of("q=LAMP"), nameHasLamp);
    assertFilters(List.of("category=Home", "inStock=true"), home.and(inStock));
    assertFilters(
        List.of("category=Home", "minPriceCents=5000", "maxPriceCents=30000"), home.and(midPrice));
    assertFilters(
        List.of("category=Home", "inStock=true", "q=lamp"), home.and(inStock).and(nameHasLamp));
    assertFilters(
        List.of(
            "category=Home", "minPriceCents=5000", "maxPriceCents=30000", "inStock=true", "q=lamp"),
        home.and(midPrice).and(inStock).and(nameHasLamp));
  }

  @Test
  void everyReturnedProductMatchesTheFilters() throws Exception {
    JsonNode page =
        getJson(
            get(URL)
                .param("category", "Kitchen")
                .param("minPriceCents", "2000")
                .param("maxPriceCents", "40000")
                .param("inStock", "true")
                .param("q", "e")
                .param("size", "100"));

    assertThat(page.at("/data/content").size()).isPositive();
    for (JsonNode product : page.at("/data/content")) {
      assertThat(product.get("category").asText()).isEqualTo("Kitchen");
      assertThat(product.get("priceCents").asLong()).isBetween(2_000L, 40_000L);
      assertThat(product.get("stock").asInt()).isPositive();
      assertThat(product.get("name").asText().toLowerCase(Locale.ROOT)).contains("e");
    }
  }

  @Test
  void filtersThatMatchNothingGiveAnEmptyPage() throws Exception {
    mvc.perform(authorized(get(URL).param("category", "Nothing Like This")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.totalPages").value(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", "category", "priceCents", "stock", "rating", "createdAt"})
  void sortsByEveryField(String field) throws Exception {
    for (String direction : List.of("asc", "desc")) {
      JsonNode page = getJson(get(URL).param("size", "100").param("sort", field + "," + direction));

      List<String> values = new ArrayList<>();
      page.at("/data/content").forEach(product -> values.add(product.get(field).asText()));
      Comparator<String> order = comparatorFor(field);
      List<String> expected =
          values.stream().sorted("asc".equals(direction) ? order : order.reversed()).toList();
      assertThat(values).as("sorted by %s %s", field, direction).isEqualTo(expected);
    }
  }

  @Test
  void pagingThroughTiedValuesNeverRepeatsOrSkipsAProduct() throws Exception {
    Set<String> seen = new HashSet<>();
    int pages = 0;

    for (int page = 0; page < 15; page++) {
      JsonNode result =
          getJson(
              get(URL).param("size", "7").param("page", "" + page).param("sort", "rating,desc"));
      for (JsonNode product : result.at("/data/content")) {
        assertThat(seen.add(product.get("id").asText())).as("no repeated product").isTrue();
      }
      pages++;
    }

    assertThat(pages).isEqualTo(15);
    assertThat(seen).hasSize(100);
  }

  @Test
  void sortingByAnUnknownFieldGives400() throws Exception {
    mvc.perform(authorized(get(URL).param("sort", "price,asc")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
        .andExpect(jsonPath("$.message").value("Cannot sort by 'price'"));
  }

  @Test
  void aMinimumPriceAboveTheMaximumGives400() throws Exception {
    mvc.perform(authorized(get(URL).param("minPriceCents", "9000").param("maxPriceCents", "100")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PRICE_RANGE"));
  }

  @Test
  void aSingleProductLooksUpUpdatesAndDisappearsWithoutStaleData() throws Exception {
    String body =
        "{\"name\":\"Cache Kettle\",\"category\":\"Home\",\"priceCents\":2500,\"stock\":5,\"rating\":4.5}";
    JsonNode created = getJson(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    String id = created.at("/data/id").asText();
    try {
      assertThat(getJson(get(URL + "/" + id)).at("/data/stock").asInt()).isEqualTo(5);
      assertThat(getJson(get(URL + "/" + id)).at("/data/stock").asInt()).isEqualTo(5);

      String changed =
          "{\"name\":\"Cache Kettle\",\"category\":\"Home\",\"priceCents\":2600,\"stock\":0,\"rating\":4.5}";
      mvc.perform(
              authorized(
                  put(URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(changed)))
          .andExpect(status().isOk());

      JsonNode afterUpdate = getJson(get(URL + "/" + id));
      assertThat(afterUpdate.at("/data/stock").asInt()).isZero();
      assertThat(afterUpdate.at("/data/priceCents").asLong()).isEqualTo(2600);
      assertThat(
              getJson(get(URL).param("q", "cache kettle").param("inStock", "true"))
                  .at("/data/totalElements")
                  .asInt())
          .as("the list sees the update too")
          .isZero();

      mvc.perform(authorized(delete(URL + "/" + id))).andExpect(status().isNoContent());

      mvc.perform(authorized(get(URL + "/" + id)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    } finally {
      productRepository.deleteById(UUID.fromString(id));
    }
  }

  private void assertFilters(List<String> parameters, Predicate<Product> expected)
      throws Exception {
    long expectedCount = seeded.stream().filter(expected).count();
    ResultActions request = mvc.perform(authorized(withParameters(parameters)));

    JsonNode page = objectMapper.readTree(request.andReturn().getResponse().getContentAsString());
    assertThat(page.at("/data/totalElements").asLong())
        .as("filters %s", parameters)
        .isEqualTo(expectedCount);
    assertThat(expectedCount).as("the filters %s should match something", parameters).isPositive();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withParameters(
      List<String> parameters) {
    var request = get(URL).param("size", "100");
    for (String parameter : parameters) {
      String[] parts = parameter.split("=", 2);
      request = request.param(parts[0], parts[1]);
    }
    return request;
  }

  private JsonNode getJson(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    String body = mvc.perform(authorized(request)).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorized(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private static Comparator<String> comparatorFor(String field) {
    return switch (field) {
      case "priceCents", "stock" -> Comparator.comparingLong(Long::parseLong);
      case "rating" -> Comparator.comparingDouble(Double::parseDouble);
      default -> Comparator.naturalOrder();
    };
  }
}
