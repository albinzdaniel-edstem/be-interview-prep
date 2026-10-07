package com.edstem.app.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

  @Bean
  public JwtEncoder jwtEncoder(JwtProperties properties) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
  }

  /**
   * The default timestamp check allows 60 seconds of clock skew, which would stretch a 15 minute
   * login to 16 minutes. The skew is set to zero so the expiry is exact.
   */
  @Bean
  public JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(signingKey(properties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
    timestampValidator.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestampValidator, new JwtIssuerValidator(properties.issuer())));
    return decoder;
  }

  private static SecretKey signingKey(JwtProperties properties) {
    return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }
}
