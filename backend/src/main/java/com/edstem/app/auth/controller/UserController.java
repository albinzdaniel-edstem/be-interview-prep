package com.edstem.app.auth.controller;

import com.edstem.app.auth.dto.response.UserResponse;
import com.edstem.app.auth.service.UserService;
import com.edstem.app.common.dto.response.ApiResponse;
import com.edstem.app.common.dto.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.ok(userService.getProfile(UUID.fromString(jwt.getSubject())));
  }

  @GetMapping
  public ApiResponse<PageResponse<UserResponse>> list(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(userService.list(pageable)));
  }
}
