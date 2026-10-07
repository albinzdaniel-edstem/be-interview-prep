package com.edstem.app.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.app.common.exception.GlobalExceptionHandler;
import com.edstem.app.product.dto.request.ProductFilter;
import com.edstem.app.product.dto.request.ProductRequest;
import com.edstem.app.product.dto.response.ProductResponse;
import com.edstem.app.product.exception.InvalidPriceRangeException;
import com.edstem.app.product.exception.ProductNotFoundException;
import com.edstem.app.product.service.ProductService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

  private static final String BASE_URL = "/api/v1/products";
  private static final String VALID_BODY =
      "{\"name\":\"Blue Kettle\",\"category\":\"Home\",\"priceCents\":2500,\"stock\":5,\"rating\":4.5}";

  @Autowired private MockMvc mvc;
  @MockitoBean private ProductService productService;

  @Test
  void createReturns201WithTheProduct() throws Exception {
    ProductResponse created = response("Blue Kettle");
    when(productService.create(any(ProductRequest.class))).thenReturn(created);

    mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(created.id().toString()))
        .andExpect(jsonPath("$.data.name").value("Blue Kettle"))
        .andExpect(jsonPath("$.data.priceCents").value(2500))
        .andExpect(jsonPath("$.data.stock").value(5))
        .andExpect(jsonPath("$.data.rating").value(4.5));
  }

  @Test
  void createReportsEveryInvalidField() throws Exception {
    String body = "{\"name\":\"\",\"category\":\"\",\"priceCents\":-1,\"stock\":-5,\"rating\":6.5}";

    mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(5)))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='name')].message")
                .value(hasItem("Name is required")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='category')].message")
                .value(hasItem("Category is required")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='priceCents')].message")
                .value(hasItem("Price must not be negative")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='stock')].message")
                .value(hasItem("Stock must not be negative")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='rating')].message")
                .value(hasItem("Rating must be between 0 and 5")));
    verify(productService, never()).create(any());
  }

  @Test
  void createRejectsMissingFields() throws Exception {
    mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(5)));
  }

  @Test
  void listPassesEveryFilterAndThePagingToTheService() throws Exception {
    when(productService.list(any(ProductFilter.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(response("Blue Kettle")), PageRequest.of(1, 5), 11));

    mvc.perform(
            get(BASE_URL)
                .param("category", "Home")
                .param("minPriceCents", "1000")
                .param("maxPriceCents", "5000")
                .param("inStock", "true")
                .param("q", "kettle")
                .param("page", "1")
                .param("size", "5")
                .param("sort", "priceCents,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(5))
        .andExpect(jsonPath("$.data.totalElements").value(11))
        .andExpect(jsonPath("$.data.totalPages").value(3));

    ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(productService).list(filter.capture(), pageable.capture());
    assertThat(filter.getValue())
        .isEqualTo(new ProductFilter("Home", 1000L, 5000L, true, "kettle"));
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    assertThat(pageable.getValue().getSort().getOrderFor("priceCents")).isNotNull();
  }

  @Test
  void listWithoutParametersUsesNoFilterAndSortsNewestFirst() throws Exception {
    when(productService.list(any(ProductFilter.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mvc.perform(get(BASE_URL)).andExpect(status().isOk());

    ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(productService).list(filter.capture(), pageable.capture());
    assertThat(filter.getValue()).isEqualTo(ProductFilter.none());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
  }

  @Test
  void listCapsThePageSizeAt100() throws Exception {
    when(productService.list(any(ProductFilter.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    mvc.perform(get(BASE_URL).param("size", "1000")).andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(productService).list(any(ProductFilter.class), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  void listRejectsANegativePrice() throws Exception {
    mvc.perform(get(BASE_URL).param("minPriceCents", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("minPriceCents"))
        .andExpect(
            jsonPath("$.error.fieldErrors[0].message").value("minPriceCents must not be negative"));
  }

  @Test
  void listRejectsAValueThatIsNotANumberOrABoolean() throws Exception {
    mvc.perform(get(BASE_URL).param("maxPriceCents", "cheap"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    mvc.perform(get(BASE_URL).param("inStock", "maybe"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
  }

  @Test
  void listRejectsSearchTextThatIsTooLong() throws Exception {
    mvc.perform(get(BASE_URL).param("q", "x".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("q"));
  }

  @Test
  void listReturns400ForAMinimumPriceAboveTheMaximum() throws Exception {
    when(productService.list(any(ProductFilter.class), any(Pageable.class)))
        .thenThrow(new InvalidPriceRangeException());

    mvc.perform(get(BASE_URL).param("minPriceCents", "5000").param("maxPriceCents", "1000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PRICE_RANGE"))
        .andExpect(jsonPath("$.error.status").value(400));
  }

  @Test
  void getReturnsTheProduct() throws Exception {
    ProductResponse product = response("Blue Kettle");
    when(productService.get(product.id())).thenReturn(product);

    mvc.perform(get(BASE_URL + "/" + product.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(product.id().toString()));
  }

  @Test
  void getReturns404ForAnUnknownProduct() throws Exception {
    UUID id = UUID.randomUUID();
    when(productService.get(id)).thenThrow(new ProductNotFoundException(id));

    mvc.perform(get(BASE_URL + "/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"))
        .andExpect(jsonPath("$.error.status").value(404));
  }

  @Test
  void getRejectsAnIdThatIsNotAUuid() throws Exception {
    mvc.perform(get(BASE_URL + "/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
  }

  @Test
  void updateReturnsTheChangedProduct() throws Exception {
    ProductResponse updated = response("Blue Kettle");
    when(productService.update(eq(updated.id()), any(ProductRequest.class))).thenReturn(updated);

    mvc.perform(
            put(BASE_URL + "/" + updated.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Blue Kettle"));
  }

  @Test
  void updateRejectsInvalidInput() throws Exception {
    String body = "{\"name\":\"A\",\"category\":\"B\",\"priceCents\":1,\"stock\":-1,\"rating\":1}";

    mvc.perform(
            put(BASE_URL + "/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("stock"));
  }

  @Test
  void updateReturns404ForAnUnknownProduct() throws Exception {
    UUID id = UUID.randomUUID();
    when(productService.update(eq(id), any(ProductRequest.class)))
        .thenThrow(new ProductNotFoundException(id));

    mvc.perform(
            put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(delete(BASE_URL + "/" + id))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(productService).delete(id);
  }

  @Test
  void deleteReturns404ForAnUnknownProduct() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new ProductNotFoundException(id)).when(productService).delete(id);

    mvc.perform(delete(BASE_URL + "/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
  }

  private static ProductResponse response(String name) {
    return new ProductResponse(UUID.randomUUID(), name, "Home", 2500, 5, 4.5, Instant.now());
  }
}
