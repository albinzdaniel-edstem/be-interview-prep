package com.edstem.app.link.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

  private final ShortCodeGenerator generator = new ShortCodeGenerator();

  @Test
  void generatesCodesOfAtMostEightUrlSafeCharacters() {
    for (int i = 0; i < 500; i++) {
      String code = generator.generate();

      assertThat(code).hasSize(ShortCodeGenerator.CODE_LENGTH);
      assertThat(code).matches("[A-Za-z0-9]{1,8}");
    }
  }

  @Test
  void generatesDifferentCodes() {
    Set<String> codes = new HashSet<>();

    for (int i = 0; i < 1000; i++) {
      codes.add(generator.generate());
    }

    assertThat(codes).hasSize(1000);
  }
}
