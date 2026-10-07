package com.edstem.app.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.admin")
public record AdminProperties(String email, String password) {

  public boolean isConfigured() {
    return email != null && !email.isBlank() && password != null && !password.isBlank();
  }
}
