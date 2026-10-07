package com.edstem.app.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edstem.app.auth.config.JwtConfig;
import com.edstem.app.auth.config.JwtProperties;
import com.edstem.app.auth.entity.Role;
import com.edstem.app.auth.entity.User;
import com.edstem.app.auth.service.TokenService.IssuedToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class TokenServiceTest {

  private static final Instant ISSUED_AT = Instant.parse("2030-01-01T12:00:00Z");

  private final JwtConfig jwtConfig = new JwtConfig();
  private JwtProperties properties;
  private TokenService tokenService;
  private User user;

  @BeforeEach
  void setUp() {
    properties = new JwtProperties(randomSecret(), "app", Duration.ofMinutes(15));
    tokenService =
        new TokenService(jwtConfig.jwtEncoder(properties), properties, clockAt(ISSUED_AT));
    user = User.builder().id(UUID.randomUUID()).email("ann@example.com").role(Role.ADMIN).build();
  }

  @Test
  void issuedTokenCarriesTheUserIdRoleIssuerAndAFifteenMinuteExpiry() {
    IssuedToken token = tokenService.issue(user);

    Jwt jwt = decoderAt(ISSUED_AT, properties).decode(token.value());

    assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("app");
    assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).isEqualTo(List.of("ADMIN"));
    assertThat(jwt.getIssuedAt()).isEqualTo(ISSUED_AT);
    assertThat(jwt.getExpiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofMinutes(15)));
    assertThat(token.expiresInSeconds()).isEqualTo(900);
  }

  @Test
  void tokenIsStillValidOneSecondBeforeItExpires() {
    IssuedToken token = tokenService.issue(user);
    Instant almostExpired = ISSUED_AT.plus(Duration.ofMinutes(15)).minusSeconds(1);

    Jwt jwt = decoderAt(almostExpired, properties).decode(token.value());

    assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
  }

  @Test
  void tokenIsRejectedOneSecondAfterItExpiresWithoutExtraTime() {
    IssuedToken token = tokenService.issue(user);
    Instant justAfterExpiry = ISSUED_AT.plus(Duration.ofMinutes(15)).plusSeconds(1);

    assertThatThrownBy(() -> decoderAt(justAfterExpiry, properties).decode(token.value()))
        .isInstanceOf(BadJwtException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void tokenSignedWithAnotherKeyIsRejected() {
    JwtProperties otherKey = new JwtProperties(randomSecret(), "app", Duration.ofMinutes(15));
    IssuedToken token = tokenService.issue(user);

    assertThatThrownBy(() -> decoderAt(ISSUED_AT, otherKey).decode(token.value()))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void tokenFromAnotherIssuerIsRejected() {
    JwtProperties otherIssuer =
        new JwtProperties(properties.secret(), "someone-else", Duration.ofMinutes(15));
    IssuedToken token = tokenService.issue(user);

    assertThatThrownBy(() -> decoderAt(ISSUED_AT, otherIssuer).decode(token.value()))
        .isInstanceOf(BadJwtException.class);
  }

  private JwtDecoder decoderAt(Instant now, JwtProperties decoderProperties) {
    return jwtConfig.jwtDecoder(decoderProperties, clockAt(now));
  }

  private static Clock clockAt(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  private static String randomSecret() {
    return UUID.randomUUID() + UUID.randomUUID().toString();
  }
}
