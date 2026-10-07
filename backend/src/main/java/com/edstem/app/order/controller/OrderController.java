package com.edstem.app.order.controller;

import com.edstem.app.common.dto.response.ApiResponse;
import com.edstem.app.order.dto.request.PlaceOrderRequest;
import com.edstem.app.order.dto.response.OrderResponse;
import com.edstem.app.order.dto.response.PlaceOrderResult;
import com.edstem.app.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  /** 201 for a new order. 200 with the first order when the request is a retry. */
  @PostMapping
  public ResponseEntity<ApiResponse<OrderResponse>> place(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key")
          @NotBlank(message = "Idempotency-Key must not be blank")
          @Size(max = 100, message = "Idempotency-Key must be at most 100 characters")
          String idempotencyKey,
      @Valid @RequestBody PlaceOrderRequest request) {
    PlaceOrderResult result =
        orderService.place(UUID.fromString(jwt.getSubject()), idempotencyKey.trim(), request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.ok(result.order()));
  }

  @PostMapping("/{id}/cancel")
  public ApiResponse<OrderResponse> cancel(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.ok(orderService.cancel(UUID.fromString(jwt.getSubject()), id));
  }
}
