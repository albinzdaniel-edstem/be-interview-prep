package com.edstem.app.link.controller;

import com.edstem.app.link.service.LinkService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
    return redirectTo(linkService.visit(code));
  }

  /**
   * HEAD gets the same redirect but is not counted. It is sent by link checkers and preview bots,
   * not by people opening the link, so counting it would inflate the visit count.
   */
  @RequestMapping(value = "/s/{code}", method = RequestMethod.HEAD)
  public ResponseEntity<Void> check(@PathVariable String code) {
    return redirectTo(linkService.resolve(code));
  }

  private static ResponseEntity<Void> redirectTo(String target) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(target))
        .cacheControl(CacheControl.noStore())
        .build();
  }
}
