package com.edstem.app.link.service;

import com.edstem.app.link.dto.request.ShortenUrlRequest;
import com.edstem.app.link.dto.response.LinkResponse;
import com.edstem.app.link.dto.response.LinkStatsResponse;
import com.edstem.app.link.dto.response.ShortenResult;
import com.edstem.app.link.entity.Link;
import com.edstem.app.link.exception.LinkExpiredException;
import com.edstem.app.link.exception.LinkNotFoundException;
import com.edstem.app.link.exception.ShortCodeUnavailableException;
import com.edstem.app.link.mapper.LinkMapper;
import com.edstem.app.link.repository.LinkRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkService {

  private static final int MAX_CODE_ATTEMPTS = 5;

  private final LinkRepository linkRepository;
  private final ShortCodeGenerator codeGenerator;
  private final LinkMapper linkMapper;
  private final Clock clock;

  /**
   * Not transactional on purpose. Each repository call commits on its own, so a unique-constraint
   * clash from a concurrent request cannot poison a surrounding transaction and can be retried.
   */
  public ShortenResult shorten(ShortenUrlRequest request) {
    String url = request.url().trim();
    String urlHash = hash(url);

    Optional<Link> existing = linkRepository.findByUrlHash(urlHash);
    if (existing.isPresent()) {
      return new ShortenResult(reuse(existing.get(), request.expiresAt()), false);
    }
    return createNew(url, urlHash, request.expiresAt());
  }

  @Transactional
  public String visit(String code) {
    Link link = findByCode(code);
    if (link.isExpiredAt(clock.instant())) {
      throw new LinkExpiredException(code);
    }
    linkRepository.incrementVisitCount(link.getId());
    return link.getOriginalUrl();
  }

  @Transactional(readOnly = true)
  public LinkStatsResponse stats(String code) {
    return linkMapper.toStats(findByCode(code));
  }

  private LinkResponse reuse(Link link, Instant requestedExpiry) {
    if (link.isExpiredAt(clock.instant())) {
      link.setExpiresAt(requestedExpiry);
      link = linkRepository.save(link);
      log.info("Renewed expired short link {}", link.getCode());
    }
    return linkMapper.toResponse(link);
  }

  private ShortenResult createNew(String url, String urlHash, Instant expiresAt) {
    for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
      Link link =
          Link.builder()
              .code(codeGenerator.generate())
              .originalUrl(url)
              .urlHash(urlHash)
              .expiresAt(expiresAt)
              .build();
      try {
        Link saved = linkRepository.saveAndFlush(link);
        log.info("Created short link {}", saved.getCode());
        return new ShortenResult(linkMapper.toResponse(saved), true);
      } catch (DataIntegrityViolationException e) {
        Optional<Link> savedByAnotherRequest = linkRepository.findByUrlHash(urlHash);
        if (savedByAnotherRequest.isPresent()) {
          return new ShortenResult(linkMapper.toResponse(savedByAnotherRequest.get()), false);
        }
        log.warn("Short code clash on attempt {}, trying another code", attempt);
      }
    }
    throw new ShortCodeUnavailableException();
  }

  private Link findByCode(String code) {
    return linkRepository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
  }

  private static String hash(String url) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(url.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
