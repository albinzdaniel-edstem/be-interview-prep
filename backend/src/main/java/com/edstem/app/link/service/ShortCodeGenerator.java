package com.edstem.app.link.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

  public static final int CODE_LENGTH = 8;

  private static final char[] ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    char[] code = new char[CODE_LENGTH];
    for (int i = 0; i < CODE_LENGTH; i++) {
      code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    return new String(code);
  }
}
