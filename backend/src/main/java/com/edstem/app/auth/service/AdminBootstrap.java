package com.edstem.app.auth.service;

import com.edstem.app.auth.config.AdminProperties;
import com.edstem.app.auth.entity.Role;
import com.edstem.app.auth.entity.User;
import com.edstem.app.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from ADMIN_EMAIL and ADMIN_PASSWORD. Registration only ever creates the
 * USER role, so this is the only way an admin account comes into existence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

  private static final int MIN_PASSWORD_LENGTH = 8;

  private final AdminProperties properties;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isConfigured()) {
      log.info("No admin account created: ADMIN_EMAIL and ADMIN_PASSWORD are not both set");
      return;
    }
    if (properties.password().length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalStateException(
          "ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    String email = Emails.normalize(properties.email());
    if (userRepository.existsByEmail(email)) {
      log.info("Admin account already exists");
      return;
    }
    User admin =
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(properties.password()))
            .role(Role.ADMIN)
            .build();
    try {
      userRepository.saveAndFlush(admin);
      log.info("Admin account created");
    } catch (DataIntegrityViolationException e) {
      log.info("Admin account was created by another instance");
    }
  }
}
