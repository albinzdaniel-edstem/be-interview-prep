package com.edstem.app.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class MaxUtf8BytesValidatorTest {

  private final MaxUtf8BytesValidator validator = validatorWithLimit(72);

  @Test
  void acceptsAValueWithinTheLimit() {
    assertThat(validator.isValid("a".repeat(72), null)).isTrue();
  }

  @Test
  void rejectsAValueOverTheLimit() {
    assertThat(validator.isValid("a".repeat(73), null)).isFalse();
  }

  @Test
  void countsBytesNotCharacters() {
    String thirtySixTwoByteCharacters = "é".repeat(36);
    String thirtySevenTwoByteCharacters = "é".repeat(37);

    assertThat(validator.isValid(thirtySixTwoByteCharacters, null)).isTrue();
    assertThat(validator.isValid(thirtySevenTwoByteCharacters, null)).isFalse();
  }

  @Test
  void leavesAMissingValueToTheNotBlankRule() {
    assertThat(validator.isValid(null, null)).isTrue();
  }

  private static MaxUtf8BytesValidator validatorWithLimit(int limit) {
    MaxUtf8Bytes annotation = mock(MaxUtf8Bytes.class);
    when(annotation.value()).thenReturn(limit);
    MaxUtf8BytesValidator validator = new MaxUtf8BytesValidator();
    validator.initialize(annotation);
    return validator;
  }
}
