package com.edstem.app.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edstem.app.auth.config.AdminProperties;
import com.edstem.app.auth.entity.Role;
import com.edstem.app.auth.entity.User;
import com.edstem.app.auth.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

  private static final String PASSWORD = UUID.randomUUID().toString();

  @Mock private UserRepository userRepository;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

  @Test
  void createsTheAdminWithAHashedPassword() {
    when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);

    bootstrap("Admin@Example.com", PASSWORD).run(null);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
    assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
  }

  @Test
  void doesNothingWhenTheSettingsAreMissing() {
    bootstrap("", "").run(null);
    bootstrap(null, null).run(null);
    bootstrap("admin@example.com", "").run(null);

    verify(userRepository, never()).saveAndFlush(any(User.class));
  }

  @Test
  void leavesAnExistingAdminAlone() {
    when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

    bootstrap("admin@example.com", PASSWORD).run(null);

    verify(userRepository, never()).saveAndFlush(any(User.class));
  }

  @Test
  void refusesToStartWithATooShortPassword() {
    AdminBootstrap bootstrap = bootstrap("admin@example.com", "short");

    assertThatThrownBy(() -> bootstrap.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 8");
    verify(userRepository, never()).saveAndFlush(any(User.class));
  }

  @Test
  void toleratesAnotherInstanceCreatingTheAdminFirst() {
    when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate email"));

    bootstrap("admin@example.com", PASSWORD).run(null);

    verify(userRepository).saveAndFlush(any(User.class));
  }

  private AdminBootstrap bootstrap(String email, String password) {
    return new AdminBootstrap(
        new AdminProperties(email, password), userRepository, passwordEncoder);
  }
}
