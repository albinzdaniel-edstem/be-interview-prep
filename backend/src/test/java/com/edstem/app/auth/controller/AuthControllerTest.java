package com.edstem.app.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.app.auth.dto.request.LoginRequest;
import com.edstem.app.auth.dto.request.RegisterRequest;
import com.edstem.app.auth.dto.response.AuthResponse;
import com.edstem.app.auth.dto.response.UserResponse;
import com.edstem.app.auth.entity.Role;
import com.edstem.app.auth.exception.EmailAlreadyRegisteredException;
import com.edstem.app.auth.exception.InvalidCredentialsException;
import com.edstem.app.auth.service.AuthService;
import com.edstem.app.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

  private static final String REGISTER_URL = "/api/v1/auth/register";
  private static final String LOGIN_URL = "/api/v1/auth/login";

  @Autowired private MockMvc mvc;
  @MockitoBean private AuthService authService;

  @Test
  void registerReturns201WithTheNewUserAndNoPassword() throws Exception {
    UserResponse created =
        new UserResponse(UUID.randomUUID(), "ann@example.com", Role.USER, Instant.now());
    when(authService.register(any(RegisterRequest.class))).thenReturn(created);
    String body = "{\"email\":\"ann@example.com\",\"password\":\"long enough password\"}";

    mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("ann@example.com"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.password").doesNotExist())
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
  }

  @Test
  void registerReportsEveryInvalidField() throws Exception {
    String body = "{\"email\":\"not-an-email\",\"password\":\"short\"}";

    mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(2)))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='email')].message")
                .value(hasItem("Enter a valid email address")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='password')].message")
                .value(hasItem("Password must be at least 8 characters")));
  }

  @Test
  void registerRejectsMissingFields() throws Exception {
    mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='email')].message")
                .value(hasItem("Email is required")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='password')].message")
                .value(hasItem("Password is required")));
  }

  @Test
  void registerRejectsAPasswordOver72Bytes() throws Exception {
    String body = "{\"email\":\"ann@example.com\",\"password\":\"%s\"}".formatted("a".repeat(73));

    mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("password"))
        .andExpect(
            jsonPath("$.error.fieldErrors[0].message")
                .value("Password must be at most 72 bytes long"));
  }

  @Test
  void registerReturns409ForAnEmailThatIsTaken() throws Exception {
    when(authService.register(any(RegisterRequest.class)))
        .thenThrow(new EmailAlreadyRegisteredException());
    String body = "{\"email\":\"ann@example.com\",\"password\":\"long enough password\"}";

    mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"))
        .andExpect(jsonPath("$.error.status").value(409));
  }

  @Test
  void loginReturnsABearerToken() throws Exception {
    when(authService.login(any(LoginRequest.class)))
        .thenReturn(new AuthResponse("token-value", "Bearer", 900));
    String body = "{\"email\":\"ann@example.com\",\"password\":\"long enough password\"}";

    mvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("token-value"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(900));
  }

  @Test
  void loginReturns401ForWrongCredentialsWithoutSayingWhichPartWasWrong() throws Exception {
    when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());
    String body = "{\"email\":\"ann@example.com\",\"password\":\"wrong password\"}";

    mvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error.status").value(401))
        .andExpect(jsonPath("$.message").value(not(containsString("password is wrong"))));
  }

  @Test
  void loginRejectsMissingFields() throws Exception {
    mvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(2)));
  }
}
