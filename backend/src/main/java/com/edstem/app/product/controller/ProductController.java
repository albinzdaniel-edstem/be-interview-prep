package com.edstem.app.product.controller;

import com.edstem.app.common.dto.response.ApiResponse;
import com.edstem.app.common.dto.response.PageResponse;
import com.edstem.app.product.dto.request.ProductFilter;
import com.edstem.app.product.dto.request.ProductRequest;
import com.edstem.app.product.dto.response.ProductResponse;
import com.edstem.app.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;

  @PostMapping
  public ResponseEntity<ApiResponse<ProductResponse>> create(
      @Valid @RequestBody ProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(productService.create(request)));
  }

  @GetMapping
  public ApiResponse<PageResponse<ProductResponse>> list(
      @RequestParam(required = false)
          @Size(max = 50, message = "Category must be at most 50 characters")
          String category,
      @RequestParam(required = false)
          @Min(value = 0, message = "minPriceCents must not be negative")
          Long minPriceCents,
      @RequestParam(required = false)
          @Min(value = 0, message = "maxPriceCents must not be negative")
          Long maxPriceCents,
      @RequestParam(required = false) Boolean inStock,
      @RequestParam(required = false)
          @Size(max = 100, message = "Search text must be at most 100 characters")
          String q,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    ProductFilter filter = new ProductFilter(category, minPriceCents, maxPriceCents, inStock, q);
    return ApiResponse.ok(PageResponse.from(productService.list(filter, pageable)));
  }

  @GetMapping("/{id}")
  public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(productService.get(id));
  }

  @PutMapping("/{id}")
  public ApiResponse<ProductResponse> update(
      @PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
    return ApiResponse.ok(productService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    productService.delete(id);
  }
}
