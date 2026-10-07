package com.edstem.app.link.controller;

import com.edstem.app.link.service.LinkService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

  private final LinkService linkService;

  /**
   * Answers 302, not 301, and forbids caching. A browser that cached a 301 would skip this endpoint
   * on later visits, and those visits would never be counted.
   */
  @GetMapping("/s/{code}")
  public ResponseEntity<Void> redirect(@PathVariable String code) {
    String target = linkService.visit(code);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(target))
        .cacheControl(CacheControl.noStore())
        .build();
  }
}
