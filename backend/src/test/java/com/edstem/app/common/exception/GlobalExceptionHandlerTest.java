package com.edstem.app.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.app.common.dto.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    controllers = {
      GlobalExceptionHandlerTest.ProbeController.class,
      GlobalExceptionHandlerTest.ValidatedProbeController.class
    })
@Import({
  GlobalExceptionHandler.class,
  GlobalExceptionHandlerTest.ProbeController.class,
  GlobalExceptionHandlerTest.ValidatedProbeController.class
})
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mvc;

  @Test
  void invalidBodyReturnsFieldErrors() throws Exception {
    String body = "{\"name\":\"\",\"count\":0}";

    mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.status").value(400))
        .andExpect(jsonPath("$.error.path").value("/probe/body"))
        .andExpect(jsonPath("$.error.timestamp").isNotEmpty())
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(2)))
        .andExpect(jsonPath("$.error.fieldErrors[?(@.field=='name')]").exists())
        .andExpect(jsonPath("$.error.fieldErrors[?(@.field=='count')]").exists());
  }

  @Test
  void malformedBodyReturnsMalformedRequest() throws Exception {
    mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.error.status").value(400));
  }

  @Test
  void wrongNumberInBodyReturnsFieldError() throws Exception {
    String body = "{\"name\":\"a\",\"count\":\"abc\"}";

    mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("count"))
        .andExpect(jsonPath("$.error.fieldErrors[0].message").value("Invalid value 'abc'"));
  }

  @Test
  void unknownEnumValueInBodyListsAllowedValues() throws Exception {
    String body = "{\"name\":\"a\",\"count\":1,\"level\":\"HUGE\"}";

    mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("level"))
        .andExpect(
            jsonPath("$.error.fieldErrors[0].message")
                .value("Invalid value 'HUGE'. Allowed values: LOW, HIGH"));
  }

  @Test
  void unknownSortPropertyReturnsInvalidParameter() throws Exception {
    mvc.perform(get("/probe/sort"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
        .andExpect(jsonPath("$.message").value("Cannot sort by 'bogus'"));
  }

  @Test
  void wrongParameterTypeReturnsInvalidParameter() throws Exception {
    mvc.perform(get("/probe/number").param("value", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
        .andExpect(jsonPath("$.message").value(containsString("'value'")));
  }

  @Test
  void parameterConstraintOnPlainControllerReturnsFieldError() throws Exception {
    mvc.perform(get("/probe/min").param("value", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("value"));
  }

  @Test
  void parameterConstraintOnValidatedControllerReturnsFieldError() throws Exception {
    mvc.perform(get("/validated/min").param("value", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("value"));
  }

  @Test
  void unknownRouteReturnsNotFound() throws Exception {
    mvc.perform(get("/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Resource not found"))
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.error.status").value(404));
  }

  @Test
  void wrongMethodReturnsMethodNotAllowed() throws Exception {
    mvc.perform(get("/probe/body"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.message").value(containsString("not supported")))
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.error.status").value(405));
  }

  @Test
  void wrongContentTypeReturnsUnsupportedMediaType() throws Exception {
    mvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("text"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.error.status").value(415));
  }

  @Test
  void domainExceptionUsesItsOwnCodeAndStatus() throws Exception {
    mvc.perform(get("/probe/domain"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Probe is in conflict"))
        .andExpect(jsonPath("$.error.code").value("PROBE_CONFLICT"))
        .andExpect(jsonPath("$.error.status").value(409))
        .andExpect(jsonPath("$.error.fieldErrors").doesNotExist());
  }

  @Test
  void unexpectedExceptionHidesInternalDetails() throws Exception {
    mvc.perform(get("/probe/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.error.status").value(500))
        .andExpect(jsonPath("$.message").value(not(containsString("secret detail"))));
  }

  enum ProbeErrorCode implements ErrorCode {
    PROBE_CONFLICT;

    @Override
    public String getCode() {
      return name();
    }

    @Override
    public HttpStatus getStatus() {
      return HttpStatus.CONFLICT;
    }
  }

  static class ProbeException extends BaseException {

    ProbeException() {
      super(ProbeErrorCode.PROBE_CONFLICT, "Probe is in conflict");
    }
  }

  enum Level {
    LOW,
    HIGH
  }

  record Payload(@NotBlank String name, @Min(1) int count, Level level) {}

  @RestController
  @RequestMapping("/probe")
  static class ProbeController {

    @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<Void> body(@Valid @RequestBody Payload payload) {
      return ApiResponse.ok(null);
    }

    @GetMapping("/number")
    ApiResponse<Integer> number(@RequestParam Integer value) {
      return ApiResponse.ok(value);
    }

    @GetMapping("/min")
    ApiResponse<Integer> min(@RequestParam @Min(1) int value) {
      return ApiResponse.ok(value);
    }

    @GetMapping("/sort")
    ApiResponse<Void> sort() {
      throw new PropertyReferenceException("bogus", TypeInformation.of(String.class), List.of());
    }

    @GetMapping("/domain")
    ApiResponse<Void> domain() {
      throw new ProbeException();
    }

    @GetMapping("/boom")
    ApiResponse<Void> boom() {
      throw new IllegalStateException("secret detail");
    }
  }

  @Validated
  @RestController
  @RequestMapping("/validated")
  static class ValidatedProbeController {

    @GetMapping("/min")
    ApiResponse<Integer> min(@RequestParam @Min(1) int value) {
      return ApiResponse.ok(value);
    }
  }
}
