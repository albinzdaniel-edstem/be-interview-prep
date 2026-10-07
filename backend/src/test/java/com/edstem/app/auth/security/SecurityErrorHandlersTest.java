package com.edstem.app.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class SecurityErrorHandlersTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final SecurityErrorWriter writer = new SecurityErrorWriter(objectMapper);
  private final JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(writer);
  private final JsonAccessDeniedHandler deniedHandler = new JsonAccessDeniedHandler(writer);

  @Test
  void missingLoginReturns401AsJson() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks");
    MockHttpServletResponse response = new MockHttpServletResponse();

    entryPoint.commence(request, response, new BadCredentialsException("no token"));

    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(body.get("success").asBoolean()).isFalse();
    assertThat(body.at("/error/code").asText()).isEqualTo("UNAUTHENTICATED");
    assertThat(body.at("/error/status").asInt()).isEqualTo(401);
    assertThat(body.at("/error/path").asText()).isEqualTo("/api/v1/tasks");
  }

  @Test
  void expiredLoginReturns401WithItsOwnCode() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    InvalidBearerTokenException expired =
        new InvalidBearerTokenException(
            "An error occurred while attempting to decode the Jwt: Jwt expired at 2030-01-01T12:15:00Z");

    entryPoint.commence(request, response, expired);

    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(body.at("/error/code").asText()).isEqualTo("TOKEN_EXPIRED");
    assertThat(body.get("message").asText()).contains("expired");
  }

  @Test
  void aBrokenTokenIsNotReportedAsExpired() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
    MockHttpServletResponse response = new MockHttpServletResponse();

    entryPoint.commence(request, response, new InvalidBearerTokenException("Bad signature"));

    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(body.at("/error/code").asText()).isEqualTo("UNAUTHENTICATED");
  }

  @Test
  void missingRoleReturns403AsJson() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
    MockHttpServletResponse response = new MockHttpServletResponse();

    deniedHandler.handle(request, response, new AccessDeniedException("denied"));

    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(body.get("success").asBoolean()).isFalse();
    assertThat(body.at("/error/code").asText()).isEqualTo("ACCESS_DENIED");
    assertThat(body.at("/error/status").asInt()).isEqualTo(403);
    assertThat(body.at("/error/path").asText()).isEqualTo("/api/v1/users");
  }
}
