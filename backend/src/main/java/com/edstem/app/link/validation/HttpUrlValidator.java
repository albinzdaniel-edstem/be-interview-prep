package com.edstem.app.link.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return true;
    }
    try {
      URI uri = new URI(value.trim());
      String scheme = uri.getScheme();
      boolean httpScheme =
          scheme != null
              && ("http".equals(scheme.toLowerCase(Locale.ROOT))
                  || "https".equals(scheme.toLowerCase(Locale.ROOT)));
      String host = uri.getHost();
      return httpScheme && host != null && !host.isBlank();
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
