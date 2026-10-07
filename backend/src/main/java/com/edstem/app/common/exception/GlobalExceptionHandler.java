package com.edstem.app.common.exception;

import com.edstem.app.common.dto.response.ApiResponse;
import com.edstem.app.common.dto.response.ErrorDetail;
import com.edstem.app.common.dto.response.FieldErrorDetail;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(BaseException.class)
  public ResponseEntity<Object> handleBaseException(BaseException ex, HttpServletRequest request) {
    ErrorCode errorCode = ex.getErrorCode();
    return build(
        errorCode.getStatus(), errorCode.getCode(), ex.getMessage(), request.getRequestURI(), null);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<FieldErrorDetail> fieldErrors =
        ex.getConstraintViolations().stream()
            .map(
                violation -> {
                  String path = violation.getPropertyPath().toString();
                  String field = path.substring(path.lastIndexOf('.') + 1);
                  return new FieldErrorDetail(field, violation.getMessage());
                })
            .toList();
    return build(
        HttpStatus.BAD_REQUEST,
        CommonErrorCode.VALIDATION_FAILED.getCode(),
        "Validation failed",
        request.getRequestURI(),
        fieldErrors);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Object> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    return build(
        HttpStatus.UNAUTHORIZED,
        CommonErrorCode.UNAUTHENTICATED.getCode(),
        "Authentication is required to access this resource",
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Object> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return build(
        HttpStatus.FORBIDDEN,
        CommonErrorCode.ACCESS_DENIED.getCode(),
        "You do not have permission to access this resource",
        request.getRequestURI(),
        null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error on {}", request.getRequestURI(), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR,
        CommonErrorCode.INTERNAL_ERROR.getCode(),
        "Something went wrong. Please try again later.",
        request.getRequestURI(),
        null);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldErrorDetail> fieldErrors =
        ex.getBindingResult().getAllErrors().stream()
            .map(
                error ->
                    new FieldErrorDetail(
                        error instanceof FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName(),
                        error.getDefaultMessage()))
            .toList();
    return build(
        HttpStatus.BAD_REQUEST,
        CommonErrorCode.VALIDATION_FAILED.getCode(),
        "Validation failed",
        path(request),
        fieldErrors);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldErrorDetail> fieldErrors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result -> {
                  if (result instanceof ParameterErrors bodyErrors) {
                    return bodyErrors.getFieldErrors().stream()
                        .map(
                            error ->
                                new FieldErrorDetail(error.getField(), error.getDefaultMessage()));
                  }
                  return result.getResolvableErrors().stream()
                      .map(
                          error ->
                              new FieldErrorDetail(
                                  result.getMethodParameter().getParameterName(),
                                  error.getDefaultMessage()));
                })
            .toList();
    return build(
        HttpStatus.BAD_REQUEST,
        CommonErrorCode.VALIDATION_FAILED.getCode(),
        "Validation failed",
        path(request),
        fieldErrors);
  }

  @ExceptionHandler(PropertyReferenceException.class)
  public ResponseEntity<Object> handleUnknownSortProperty(
      PropertyReferenceException ex, HttpServletRequest request) {
    return build(
        HttpStatus.BAD_REQUEST,
        CommonErrorCode.INVALID_PARAMETER.getCode(),
        "Cannot sort by '%s'".formatted(ex.getPropertyName()),
        request.getRequestURI(),
        null);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (ex.getCause() instanceof InvalidFormatException invalid) {
      return build(
          HttpStatus.BAD_REQUEST,
          CommonErrorCode.VALIDATION_FAILED.getCode(),
          "Validation failed",
          path(request),
          List.of(new FieldErrorDetail(fieldName(invalid), invalidValueMessage(invalid))));
    }
    return build(
        HttpStatus.BAD_REQUEST,
        CommonErrorCode.MALFORMED_REQUEST.getCode(),
        "Request body is missing or malformed",
        path(request),
        null);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String message =
        "Parameter '%s' has an invalid value '%s'".formatted(ex.getPropertyName(), ex.getValue());
    return build(
        HttpStatus.BAD_REQUEST,
        CommonErrorCode.INVALID_PARAMETER.getCode(),
        message,
        path(request),
        null);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    String message = "Request could not be processed";
    if (ex instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null) {
      message = errorResponse.getBody().getDetail();
    }
    if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
      message = "Resource not found";
    }
    if (statusCode.is5xxServerError()) {
      log.error("Request failed on {}", path(request), ex);
      message = "Something went wrong. Please try again later.";
    }
    return build(statusCode, codeFor(statusCode).getCode(), message, path(request), null);
  }

  private static CommonErrorCode codeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> CommonErrorCode.MALFORMED_REQUEST;
      case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
      case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
      case 406 -> CommonErrorCode.NOT_ACCEPTABLE;
      case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
      default ->
          status.is5xxServerError()
              ? CommonErrorCode.INTERNAL_ERROR
              : CommonErrorCode.REQUEST_REJECTED;
    };
  }

  private static String fieldName(InvalidFormatException ex) {
    String name =
        ex.getPath().stream()
            .map(Reference::getFieldName)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("."));
    return name.isEmpty() ? "body" : name;
  }

  private static String invalidValueMessage(InvalidFormatException ex) {
    Class<?> target = ex.getTargetType();
    if (target != null && target.isEnum()) {
      String allowed =
          Arrays.stream(target.getEnumConstants())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      return "Invalid value '%s'. Allowed values: %s".formatted(ex.getValue(), allowed);
    }
    return "Invalid value '%s'".formatted(ex.getValue());
  }

  private static String path(WebRequest request) {
    return ((ServletWebRequest) request).getRequest().getRequestURI();
  }

  private static ResponseEntity<Object> build(
      HttpStatusCode status,
      String code,
      String message,
      String path,
      List<FieldErrorDetail> fieldErrors) {
    ErrorDetail error = new ErrorDetail(code, status.value(), Instant.now(), path, fieldErrors);
    return ResponseEntity.status(status).body(ApiResponse.failure(message, error));
  }
}
