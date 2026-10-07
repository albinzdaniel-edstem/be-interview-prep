package com.edstem.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.app.auth.entity.Role;
import com.edstem.app.auth.entity.User;
import com.edstem.app.auth.repository.UserRepository;
import com.edstem.app.auth.service.TokenService;
import com.edstem.app.link.repository.LinkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

  private static final String PASSWORD = UUID.randomUUID().toString();

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtEncoder jwtEncoder;

  @Test
  void requestWithoutLoginGets401AsJsonOnEveryProtectedEndpoint() throws Exception {
    List<String> protectedPaths =
        List.of("/api/v1/tasks", "/api/v1/links/abc/stats", "/api/v1/users/me", "/api/v1/users");

    for (String path : protectedPaths) {
      mvc.perform(get(path))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
          .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
          .andExpect(jsonPath("$.error.status").value(401))
          .andExpect(jsonPath("$.error.path").value(path));
    }
  }

  @Test
  void postingWithoutLoginIsRejectedBeforeAnythingIsCreated() throws Exception {
    long linksBefore = linkRepository.count();

    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/secret\"}"))
        .andExpect(status().isUnauthorized());

    assertThat(linkRepository.count()).isEqualTo(linksBefore);
  }

  @Test
  void aUserCannotReachTheAdminEndpoint() throws Exception {
    String email = newEmail();
    register(email);
    String token = login(email);

    mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
        .andExpect(jsonPath("$.error.status").value(403));
  }

  @Test
  void anAdminCanListAllUsersWithoutPasswordData() throws Exception {
    String adminEmail = newEmail();
    saveUser(adminEmail, Role.ADMIN);
    register(newEmail());
    String token = login(adminEmail);

    mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", not(hasSize(0))))
        .andExpect(jsonPath("$.data.totalElements").isNumber())
        .andExpect(jsonPath("$.data.content[0].password").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());
  }

  @Test
  void aUserSeesOnlyTheirOwnProfile() throws Exception {
    String email = newEmail();
    register(email);
    register(newEmail());
    String token = login(email);

    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(email))
        .andExpect(jsonPath("$.data.role").value("USER"));
  }

  @Test
  void aValidLoginCanUseTheTaskAndLinkEndpoints() throws Exception {
    String email = newEmail();
    register(email);
    String token = login(email);

    mvc.perform(
            post("/api/v1/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Behind a login\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/links")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/behind-login\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void registerLoginAndOpeningAShortLinkStayOpen() throws Exception {
    mvc.perform(get("/s/unknown1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("LINK_NOT_FOUND"));
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void loginFailsTheSameWayForAWrongPasswordAndAnUnknownEmail() throws Exception {
    String email = newEmail();
    register(email);

    String wrongPassword =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, "not the password")))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownEmail =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(newEmail(), PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(objectMapper.readTree(wrongPassword).get("message"))
        .isEqualTo(objectMapper.readTree(unknownEmail).get("message"));
  }

  @Test
  void anExpiredTokenGets401WithTheExpiredCode() throws Exception {
    String token = tokenWithExpiry(Instant.now().minus(Duration.ofMinutes(1)));

    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
  }

  @Test
  void aTokenWithTheWrongSignatureGets401() throws Exception {
    String token = login(registerNew());
    String tampered = token.substring(0, token.length() - 4) + "AAAA";

    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void garbageInsteadOfATokenGets401() throws Exception {
    mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theServerKeepsNoSessionBetweenRequests() throws Exception {
    String email = newEmail();
    register(email);
    String token = login(email);

    MvcResult withToken =
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andReturn();
    assertThat(withToken.getRequest().getSession(false)).isNull();

    mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void registeringTheSameEmailTwiceGets409() throws Exception {
    String email = newEmail();
    register(email);

    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email.toUpperCase(), PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void aNewAccountAlwaysGetsTheUserRoleEvenIfTheRequestAsksForAdmin() throws Exception {
    String email = newEmail();

    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"%s\",\"role\":\"ADMIN\"}"
                        .formatted(email, PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.role").value("USER"));
    assertThat(userRepository.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.USER);
  }

  @Test
  void storedPasswordsAreHashedNotPlainText() throws Exception {
    String email = newEmail();
    register(email);

    String stored = userRepository.findByEmail(email).orElseThrow().getPasswordHash();

    assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
    assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
  }

  @Test
  void theLoginResponseTellsTheClientTheTokenLivesFifteenMinutes() throws Exception {
    String email = newEmail();
    register(email);

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(900));
  }

  @Test
  void anExpiredTokenOnLoginDoesNotStopTheUserFromLoggingInAgain() throws Exception {
    String email = registerNew();
    String expired = tokenWithExpiry(Instant.now().minus(Duration.ofMinutes(1)));

    mvc.perform(
            post("/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
  }

  @Test
  void anExpiredTokenOnRegisterDoesNotBlockSigningUp() throws Exception {
    String expired = tokenWithExpiry(Instant.now().minus(Duration.ofMinutes(1)));

    mvc.perform(
            post("/api/v1/auth/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(newEmail(), PASSWORD)))
        .andExpect(status().isCreated());
  }

  @Test
  void aBrokenTokenOnAShortLinkIsIgnored() throws Exception {
    mvc.perform(get("/s/unknown1").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("LINK_NOT_FOUND"));
  }

  @Test
  void aBrokenTokenStillGets401OnAProtectedEndpoint() throws Exception {
    mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aHeadRequestToAShortLinkNeedsNoLogin() throws Exception {
    String token = login(registerNew());
    mvc.perform(
            post("/api/v1/links")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/head-check\"}"))
        .andExpect(status().isCreated());
    String code =
        linkRepository.findAll().stream()
            .filter(link -> "https://example.com/head-check".equals(link.getOriginalUrl()))
            .findFirst()
            .orElseThrow()
            .getCode();

    mvc.perform(head("/s/" + code))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/head-check"));
    mvc.perform(head("/s/unknown1")).andExpect(status().isNotFound());

    assertThat(linkRepository.findByCode(code).orElseThrow().getVisitCount()).isZero();
    mvc.perform(get("/s/" + code)).andExpect(status().isFound());
    assertThat(linkRepository.findByCode(code).orElseThrow().getVisitCount()).isEqualTo(1);
  }

  private String registerNew() throws Exception {
    String email = newEmail();
    register(email);
    return email;
  }

  private void register(String email) throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD)))
        .andExpect(status().isCreated());
  }

  private String login(String email) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode json = objectMapper.readTree(body);
    return json.at("/data/accessToken").asText();
  }

  private void saveUser(String email, Role role) {
    userRepository.saveAndFlush(
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .role(role)
            .build());
  }

  private String tokenWithExpiry(Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("app")
            .subject(UUID.randomUUID().toString())
            .issuedAt(expiresAt.minus(Duration.ofMinutes(15)))
            .expiresAt(expiresAt)
            .claim(TokenService.ROLES_CLAIM, List.of("USER"))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private static String credentials(String email, String password) {
    return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
  }

  private static String newEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }
}
