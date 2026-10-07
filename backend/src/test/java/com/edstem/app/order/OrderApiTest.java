package com.edstem.app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.edstem.app.PostgresIntegrationTest;
import com.edstem.app.product.dto.request.ProductRequest;
import com.edstem.app.product.repository.ProductRepository;
import com.edstem.app.product.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class OrderApiTest extends PostgresIntegrationTest {

  private static final String PASSWORD = UUID.randomUUID().toString();

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProductService productService;
  @Autowired private ProductRepository productRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String token;

  @BeforeEach
  void logIn() throws Exception {
    String credentials =
        "{\"email\":\"customer-%s@example.com\",\"password\":\"%s\"}"
            .formatted(UUID.randomUUID(), PASSWORD);
    mvc.perform(
        post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials));
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
  void fiftyOrdersAtTheSameMomentForTenItemsGiveExactlyTenOrdersAndZeroStock() throws Exception {
    UUID product = newProduct("Last Ten", 10);

    List<Integer> statuses =
        runAtTheSameTime(
            50,
            () ->
                place(UUID.randomUUID().toString(), orderBody(product, 1))
                    .getResponse()
                    .getStatus());

    assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(10);
    assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(40);
    assertThat(statuses).containsOnly(201, 409);
    assertThat(stockOf(product)).isZero();
    assertThat(orderedQuantity(product)).isEqualTo(10);
  }

  @Test
  void retryingTheSameRequestCreatesOnlyOneOrder() throws Exception {
    UUID product = newProduct("Retry Kettle", 10);
    String key = UUID.randomUUID().toString();

    MvcResult first = place(key, orderBody(product, 3));
    MvcResult retry = place(key, orderBody(product, 3));

    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    assertThat(retry.getResponse().getStatus()).isEqualTo(200);
    assertThat(orderId(retry)).isEqualTo(orderId(first));
    assertThat(ordersWithKey(key)).isEqualTo(1);
    assertThat(stockOf(product)).isEqualTo(7);
  }

  @Test
  void retriesThatArriveAtTheSameMomentAlsoCreateOnlyOneOrder() throws Exception {
    UUID product = newProduct("Parallel Retry", 10);
    String key = UUID.randomUUID().toString();

    List<MvcResult> results = runAtTheSameTime(10, () -> place(key, orderBody(product, 2)));

    assertThat(results.stream().map(r -> r.getResponse().getStatus()))
        .containsOnly(200, 201)
        .filteredOn(status -> status == 201)
        .hasSize(1);
    assertThat(results.stream().map(this::orderId).distinct()).hasSize(1);
    assertThat(ordersWithKey(key)).isEqualTo(1);
    assertThat(stockOf(product)).isEqualTo(8);
  }

  @Test
  void anOrderWithNotEnoughStockForOneItemReservesNothingAndReturns409WithAClearMessage()
      throws Exception {
    List<UUID> byId = twoProductsWithTheScarceOneLast();
    UUID plenty = byId.get(0);
    UUID scarce = byId.get(1);
    String key = UUID.randomUUID().toString();

    MvcResult result = place(key, orderBody(List.of(plenty, scarce), 2));

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat(body.at("/error/code").asText()).isEqualTo("INSUFFICIENT_STOCK");
    assertThat(body.get("message").asText())
        .contains("Scarce")
        .contains("requested 2")
        .contains("available 1");
    assertThat(stockOf(plenty)).as("the first item was not kept").isEqualTo(5);
    assertThat(stockOf(scarce)).isEqualTo(1);
    assertThat(ordersWithKey(key)).isZero();
  }

  @Test
  void cancellingAnOrderReturnsTheStockOfEveryItem() throws Exception {
    UUID kettle = newProduct("Cancel Kettle", 10);
    UUID lamp = newProduct("Cancel Lamp", 4);
    MvcResult placed = place(UUID.randomUUID().toString(), orderBody(List.of(kettle, lamp), 3));
    assertThat(stockOf(kettle)).isEqualTo(7);
    assertThat(stockOf(lamp)).isEqualTo(1);

    MvcResult cancelled =
        mvc.perform(
                post("/api/v1/orders/" + orderId(placed) + "/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();

    assertThat(cancelled.getResponse().getStatus()).isEqualTo(200);
    assertThat(
            objectMapper
                .readTree(cancelled.getResponse().getContentAsString())
                .at("/data/status")
                .asText())
        .isEqualTo("CANCELLED");
    assertThat(stockOf(kettle)).isEqualTo(10);
    assertThat(stockOf(lamp)).isEqualTo(4);
  }

  private MvcResult place(String idempotencyKey, String body) throws Exception {
    return mvc.perform(
            post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  private String orderId(MvcResult result) {
    try {
      return objectMapper
          .readTree(result.getResponse().getContentAsString())
          .at("/data/id")
          .asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private UUID newProduct(String name, int stock) {
    return productService.create(new ProductRequest(name, "Home", 1000L, stock, 4.0)).id();
  }

  /**
   * Returns two products. Products are reserved in id order, so the one with the smaller id must be
   * the one with plenty of stock. It is then reserved first and has to be rolled back when the
   * second one runs out.
   */
  private List<UUID> twoProductsWithTheScarceOneLast() {
    List<UUID> ids = new ArrayList<>(List.of(newProduct("A", 1), newProduct("B", 1)));
    ids.sort(Comparator.naturalOrder());
    productService.update(ids.get(0), new ProductRequest("Plenty", "Home", 1000L, 5, 4.0));
    productService.update(ids.get(1), new ProductRequest("Scarce", "Home", 1000L, 1, 4.0));
    return ids;
  }

  private int stockOf(UUID productId) {
    return productRepository.findById(productId).orElseThrow().getStock();
  }

  private int orderedQuantity(UUID productId) {
    Integer total =
        jdbcTemplate.queryForObject(
            "select coalesce(sum(quantity), 0) from order_items where product_id = ?",
            Integer.class,
            productId);
    return total;
  }

  private int ordersWithKey(String key) {
    return jdbcTemplate.queryForObject(
        "select count(*) from orders where idempotency_key = ?", Integer.class, key);
  }

  private static String orderBody(UUID product, int quantity) {
    return orderBody(List.of(product), quantity);
  }

  private static String orderBody(List<UUID> products, int quantity) {
    StringBuilder items = new StringBuilder();
    for (UUID product : products) {
      if (items.length() > 0) {
        items.append(',');
      }
      items.append("{\"productId\":\"%s\",\"quantity\":%d}".formatted(product, quantity));
    }
    return "{\"items\":[" + items + "]}";
  }

  private static <T> List<T> runAtTheSameTime(int threads, Callable<T> task) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return task.call();
                }));
      }
      ready.await();
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }
}
