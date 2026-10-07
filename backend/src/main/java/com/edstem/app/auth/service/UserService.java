package com.edstem.app.auth.service;

import com.edstem.app.auth.dto.response.UserResponse;
import com.edstem.app.auth.exception.UserNotFoundException;
import com.edstem.app.auth.mapper.UserMapper;
import com.edstem.app.auth.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;

  @Transactional(readOnly = true)
  public UserResponse getProfile(UUID userId) {
    return userRepository
        .findById(userId)
        .map(userMapper::toResponse)
        .orElseThrow(UserNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public Page<UserResponse> list(Pageable pageable) {
    return userRepository.findAll(pageable).map(userMapper::toResponse);
  }
}
