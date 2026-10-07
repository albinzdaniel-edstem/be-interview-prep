package com.edstem.app.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.edstem.app.auth.dto.response.UserResponse;
import com.edstem.app.auth.entity.Role;
import com.edstem.app.auth.entity.User;
import com.edstem.app.auth.exception.UserNotFoundException;
import com.edstem.app.auth.mapper.UserMapper;
import com.edstem.app.auth.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Spy private UserMapper userMapper = new UserMapper();
  @InjectMocks private UserService userService;

  @Test
  void getProfileReturnsTheUser() {
    User user = user("ann@example.com", Role.USER);
    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

    UserResponse profile = userService.getProfile(user.getId());

    assertThat(profile.id()).isEqualTo(user.getId());
    assertThat(profile.email()).isEqualTo("ann@example.com");
    assertThat(profile.role()).isEqualTo(Role.USER);
  }

  @Test
  void getProfileFailsWhenTheUserNoLongerExists() {
    UUID id = UUID.randomUUID();
    when(userRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getProfile(id)).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void listReturnsAPageOfUsers() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<User> page =
        new PageImpl<>(
            List.of(user("ann@example.com", Role.USER), user("bob@example.com", Role.ADMIN)));
    when(userRepository.findAll(pageable)).thenReturn(page);

    Page<UserResponse> result = userService.list(pageable);

    assertThat(result.getContent())
        .extracting(UserResponse::email)
        .containsExactly("ann@example.com", "bob@example.com");
  }

  @Test
  void theUserResponseHasNoPasswordField() {
    assertThat(UserResponse.class.getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("passwordHash", "password");
  }

  private static User user(String email, Role role) {
    return User.builder()
        .id(UUID.randomUUID())
        .email(email)
        .passwordHash("hash")
        .role(role)
        .build();
  }
}
