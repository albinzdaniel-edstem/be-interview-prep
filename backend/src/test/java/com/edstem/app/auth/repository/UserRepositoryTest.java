package com.edstem.app.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edstem.app.auth.entity.Role;
import com.edstem.app.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  @Test
  void savesAUserWithGeneratedIdAndCreatedDate() {
    User saved = userRepository.saveAndFlush(newUser("ann@example.com", Role.USER));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getRole()).isEqualTo(Role.USER);
  }

  @Test
  void findsAUserByEmail() {
    userRepository.saveAndFlush(newUser("ann@example.com", Role.ADMIN));

    assertThat(userRepository.findByEmail("ann@example.com"))
        .get()
        .extracting(User::getRole)
        .isEqualTo(Role.ADMIN);
    assertThat(userRepository.findByEmail("bob@example.com")).isEmpty();
  }

  @Test
  void tellsWhetherAnEmailIsTaken() {
    userRepository.saveAndFlush(newUser("ann@example.com", Role.USER));

    assertThat(userRepository.existsByEmail("ann@example.com")).isTrue();
    assertThat(userRepository.existsByEmail("bob@example.com")).isFalse();
  }

  @Test
  void rejectsADuplicateEmail() {
    userRepository.saveAndFlush(newUser("ann@example.com", Role.USER));
    User duplicate = newUser("ann@example.com", Role.USER);

    assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static User newUser(String email, Role role) {
    return User.builder().email(email).passwordHash("hashed-value").role(role).build();
  }
}
