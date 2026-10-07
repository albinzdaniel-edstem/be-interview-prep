package com.edstem.app.common.dto.response;

public record ApiResponse<T>(boolean success, T data, String message, ErrorDetail error) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null);
  }

  public static <T> ApiResponse<T> ok(T data, String message) {
    return new ApiResponse<>(true, data, message, null);
  }

  public static ApiResponse<Void> failure(String message, ErrorDetail error) {
    return new ApiResponse<>(false, null, message, error);
  }
}
