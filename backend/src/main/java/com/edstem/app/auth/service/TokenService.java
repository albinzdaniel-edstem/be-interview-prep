package com.edstem.app.auth.service;

import com.edstem.app.auth.config.JwtProperties;
import com.edstem.app.auth.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

  public static final String ROLES_CLAIM = "roles";

  private final JwtEncoder jwtEncoder;
  private final JwtProperties properties;
  private final Clock clock;

  public IssuedToken issue(User user) {
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(properties.ttl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(user.getId().toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim(ROLES_CLAIM, List.of(user.getRole().name()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedToken(value, properties.ttl().toSeconds());
  }

  public record IssuedToken(String value, long expiresInSeconds) {}
}
