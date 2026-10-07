package com.edstem.app.auth.mapper;

import com.edstem.app.auth.dto.response.UserResponse;
import com.edstem.app.auth.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public UserResponse toResponse(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
  }
}
