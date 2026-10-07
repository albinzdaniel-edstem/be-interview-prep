package com.edstem.app.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edstem.app.auth.dto.request.LoginRequest;
import com.edstem.app.auth.dto.request.RegisterRequest;
import com.edstem.app.auth.dto.response.AuthResponse;
import com.edstem.app.auth.dto.response.UserResponse;
import com.edstem.app.auth.entity.Role;
import com.edstem.app.auth.entity.User;
import com.edstem.app.auth.exception.EmailAlreadyRegisteredException;
import com.edstem.app.auth.exception.InvalidCredentialsException;
import com.edstem.app.auth.mapper.UserMapper;
import com.edstem.app.auth.repository.UserRepository;
import com.edstem.app.auth.service.TokenService.IssuedToken;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String PASSWORD = "correct horse battery";

  @Mock private UserRepository userRepository;
  @Mock private TokenService tokenService;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(userRepository, passwordEncoder, tokenService, new UserMapper());
  }

  @Test
  void registerStoresALowerCaseEmailAndAHashedPasswordWithTheUserRole() {
    when(userRepository.existsByEmail("ann@example.com")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

    UserResponse response =
        authService.register(new RegisterRequest("  Ann@Example.COM ", PASSWORD));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("ann@example.com");
    assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
    assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
    assertThat(response.email()).isEqualTo("ann@example.com");
    assertThat(response.role()).isEqualTo(Role.USER);
  }

  @Test
  void registerRejectsAnEmailThatIsAlreadyTaken() {
    when(userRepository.existsByEmail("ann@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(new RegisterRequest("ann@example.com", PASSWORD)))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(userRepository, never()).saveAndFlush(any(User.class));
  }

  @Test
  void registerGivesTheSameErrorWhenTwoRequestsRaceForTheSameEmail() {
    when(userRepository.existsByEmail(anyString())).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate email"));

    assertThatThrownBy(() -> authService.register(new RegisterRequest("ann@example.com", PASSWORD)))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  void loginReturnsABearerTokenForTheRightPassword() {
    User user = storedUser("ann@example.com", Role.USER);
    when(userRepository.findByEmail("ann@example.com")).thenReturn(Optional.of(user));
    when(tokenService.issue(user)).thenReturn(new IssuedToken("token-value", 900));

    AuthResponse response = authService.login(new LoginRequest(" ANN@example.com", PASSWORD));

    assertThat(response.accessToken()).isEqualTo("token-value");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(900);
  }

  @Test
  void loginRejectsAWrongPassword() {
    User user = storedUser("ann@example.com", Role.USER);
    when(userRepository.findByEmail("ann@example.com")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("ann@example.com", "wrong password")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(tokenService, never()).issue(any(User.class));
  }

  @Test
  void loginGivesTheSameErrorForAnUnknownEmail() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD)))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Email or password is incorrect");
    verify(tokenService, never()).issue(any(User.class));
  }

  private User storedUser(String email, Role role) {
    return User.builder()
        .id(UUID.randomUUID())
        .email(email)
        .passwordHash(passwordEncoder.encode(PASSWORD))
        .role(role)
        .build();
  }
}
