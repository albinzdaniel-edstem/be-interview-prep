package com.edstem.app.auth.service;

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
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

  private static final String TOKEN_TYPE = "Bearer";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final UserMapper userMapper;
  private final String unknownUserHash;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      TokenService tokenService,
      UserMapper userMapper) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.userMapper = userMapper;
    this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Not transactional on purpose. Two requests that register the same email at the same moment both
   * pass the existence check. The unique constraint stops the second insert, and it is caught here
   * so the caller gets the same 409 either way.
   */
  public UserResponse register(RegisterRequest request) {
    String email = Emails.normalize(request.email());
    if (userRepository.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException();
    }
    User user =
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(Role.USER)
            .build();
    try {
      User saved = userRepository.saveAndFlush(user);
      log.info("Registered user {}", saved.getId());
      return userMapper.toResponse(saved);
    } catch (DataIntegrityViolationException e) {
      throw new EmailAlreadyRegisteredException();
    }
  }

  /**
   * An unknown email and a wrong password give the same error. For an unknown email the password is
   * still checked against a throwaway hash, so both cases take about the same time.
   */
  public AuthResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(Emails.normalize(request.email())).orElse(null);
    String hash = user != null ? user.getPasswordHash() : unknownUserHash;
    boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
    if (user == null || !passwordMatches) {
      throw new InvalidCredentialsException();
    }
    IssuedToken token = tokenService.issue(user);
    log.info("User {} logged in", user.getId());
    return new AuthResponse(token.value(), TOKEN_TYPE, token.expiresInSeconds());
  }
}
