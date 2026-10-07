package com.edstem.app.link.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpUrlValidatorTest {

  private final HttpUrlValidator validator = new HttpUrlValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.com",
        "https://example.com/path?query=1#part",
        "HTTPS://Example.com",
        "http://localhost:8080/health",
        "  https://example.com  "
      })
  void acceptsHttpAndHttpsUrls(String url) {
    assertThat(validator.isValid(url, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "example.com",
        "ftp://example.com/file",
        "javascript:alert(1)",
        "http://",
        "http:///path",
        "https://exa mple.com",
        "not a url",
        "//example.com"
      })
  void rejectsEverythingElse(String url) {
    assertThat(validator.isValid(url, null)).isFalse();
  }

  @Test
  void leavesBlankAndMissingValuesToTheNotBlankRule() {
    assertThat(validator.isValid(null, null)).isTrue();
    assertThat(validator.isValid("  ", null)).isTrue();
  }
}
