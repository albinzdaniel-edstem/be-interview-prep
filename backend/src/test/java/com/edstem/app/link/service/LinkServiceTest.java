package com.edstem.app.link.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edstem.app.link.config.LinkProperties;
import com.edstem.app.link.dto.request.ShortenUrlRequest;
import com.edstem.app.link.dto.response.LinkStatsResponse;
import com.edstem.app.link.dto.response.ShortenResult;
import com.edstem.app.link.entity.Link;
import com.edstem.app.link.exception.LinkExpiredException;
import com.edstem.app.link.exception.LinkNotFoundException;
import com.edstem.app.link.exception.ShortCodeUnavailableException;
import com.edstem.app.link.mapper.LinkMapper;
import com.edstem.app.link.repository.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

  private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
  private static final String URL = "https://example.com/a/long/path";

  @Mock private LinkRepository linkRepository;
  @Mock private ShortCodeGenerator codeGenerator;

  private LinkService linkService;

  @BeforeEach
  void setUp() {
    LinkMapper mapper = new LinkMapper(new LinkProperties("http://short.test/"));
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    linkService = new LinkService(linkRepository, codeGenerator, mapper, clock);
  }

  @Test
  void shortenCreatesANewLinkWithAShortUrl() {
    Instant expiry = NOW.plusSeconds(3600);
    when(linkRepository.findByUrlHash(anyString())).thenReturn(Optional.empty());
    when(codeGenerator.generate()).thenReturn("abc12345");
    when(linkRepository.saveAndFlush(any(Link.class))).thenAnswer(call -> call.getArgument(0));

    ShortenResult result = linkService.shorten(new ShortenUrlRequest("  " + URL + "  ", expiry));

    assertThat(result.created()).isTrue();
    assertThat(result.link().code()).isEqualTo("abc12345");
    assertThat(result.link().shortUrl()).isEqualTo("http://short.test/s/abc12345");
    assertThat(result.link().originalUrl()).isEqualTo(URL);
    assertThat(result.link().expiresAt()).isEqualTo(expiry);
  }

  @Test
  void shortenReturnsTheExistingLinkForTheSameUrl() {
    Link existing = link("abc12345", null);
    when(linkRepository.findByUrlHash(anyString())).thenReturn(Optional.of(existing));

    ShortenResult result = linkService.shorten(new ShortenUrlRequest(URL, NOW.plusSeconds(60)));

    assertThat(result.created()).isFalse();
    assertThat(result.link().code()).isEqualTo("abc12345");
    assertThat(result.link().expiresAt()).isNull();
    verify(linkRepository, never()).save(any(Link.class));
    verify(linkRepository, never()).saveAndFlush(any(Link.class));
  }

  @Test
  void shortenRenewsAnExpiredLinkAndKeepsItsCode() {
    Link expired = link("abc12345", NOW.minusSeconds(1));
    Instant newExpiry = NOW.plusSeconds(86_400);
    when(linkRepository.findByUrlHash(anyString())).thenReturn(Optional.of(expired));
    when(linkRepository.save(expired)).thenReturn(expired);

    ShortenResult result = linkService.shorten(new ShortenUrlRequest(URL, newExpiry));

    assertThat(result.created()).isFalse();
    assertThat(result.link().code()).isEqualTo("abc12345");
    assertThat(result.link().expiresAt()).isEqualTo(newExpiry);
  }

  @Test
  void shortenReturnsTheLinkSavedByAnotherRequestAtTheSameTime() {
    Link savedElsewhere = link("zzz99999", null);
    when(linkRepository.findByUrlHash(anyString()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(savedElsewhere));
    when(codeGenerator.generate()).thenReturn("abc12345");
    when(linkRepository.saveAndFlush(any(Link.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate url hash"));

    ShortenResult result = linkService.shorten(new ShortenUrlRequest(URL, null));

    assertThat(result.created()).isFalse();
    assertThat(result.link().code()).isEqualTo("zzz99999");
  }

  @Test
  void shortenTriesAnotherCodeWhenTheCodeIsTaken() {
    when(linkRepository.findByUrlHash(anyString())).thenReturn(Optional.empty());
    when(codeGenerator.generate()).thenReturn("taken123", "free4567");
    when(linkRepository.saveAndFlush(any(Link.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate code"))
        .thenAnswer(call -> call.getArgument(0));

    ShortenResult result = linkService.shorten(new ShortenUrlRequest(URL, null));

    assertThat(result.created()).isTrue();
    assertThat(result.link().code()).isEqualTo("free4567");
  }

  @Test
  void shortenGivesUpAfterFiveCodeClashes() {
    when(linkRepository.findByUrlHash(anyString())).thenReturn(Optional.empty());
    when(codeGenerator.generate()).thenReturn("taken123");
    when(linkRepository.saveAndFlush(any(Link.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate code"));

    assertThatThrownBy(() -> linkService.shorten(new ShortenUrlRequest(URL, null)))
        .isInstanceOf(ShortCodeUnavailableException.class);
    verify(linkRepository, times(5)).saveAndFlush(any(Link.class));
  }

  @Test
  void visitCountsTheVisitAndReturnsTheOriginalUrl() {
    Link link = link("abc12345", NOW.plusSeconds(60));
    when(linkRepository.findByCode("abc12345")).thenReturn(Optional.of(link));

    String target = linkService.visit("abc12345");

    assertThat(target).isEqualTo(URL);
    verify(linkRepository).incrementVisitCount(link.getId());
  }

  @Test
  void visitWorksForALinkWithoutAnExpiry() {
    Link link = link("abc12345", null);
    when(linkRepository.findByCode("abc12345")).thenReturn(Optional.of(link));

    String target = linkService.visit("abc12345");

    assertThat(target).isEqualTo(URL);
  }

  @Test
  void visitRejectsAnUnknownCodeWithoutCounting() {
    when(linkRepository.findByCode("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> linkService.visit("nope")).isInstanceOf(LinkNotFoundException.class);
    verify(linkRepository, never()).incrementVisitCount(any());
  }

  @Test
  void visitRejectsAnExpiredCodeWithoutCounting() {
    Link link = link("abc12345", NOW.minusSeconds(1));
    when(linkRepository.findByCode("abc12345")).thenReturn(Optional.of(link));

    assertThatThrownBy(() -> linkService.visit("abc12345"))
        .isInstanceOf(LinkExpiredException.class);
    verify(linkRepository, never()).incrementVisitCount(any());
  }

  @Test
  void visitTreatsTheExpiryInstantItselfAsExpired() {
    Link link = link("abc12345", NOW);
    when(linkRepository.findByCode("abc12345")).thenReturn(Optional.of(link));

    assertThatThrownBy(() -> linkService.visit("abc12345"))
        .isInstanceOf(LinkExpiredException.class);
  }

  @Test
  void statsShowTheOriginalUrlVisitCountAndCreatedDate() {
    Link link = link("abc12345", null);
    link.setVisitCount(7);
    link.setCreatedAt(NOW.minusSeconds(100));
    when(linkRepository.findByCode("abc12345")).thenReturn(Optional.of(link));

    LinkStatsResponse stats = linkService.stats("abc12345");

    assertThat(stats.originalUrl()).isEqualTo(URL);
    assertThat(stats.visitCount()).isEqualTo(7);
    assertThat(stats.createdAt()).isEqualTo(NOW.minusSeconds(100));
  }

  @Test
  void statsRejectAnUnknownCode() {
    when(linkRepository.findByCode("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> linkService.stats("nope")).isInstanceOf(LinkNotFoundException.class);
  }

  private static Link link(String code, Instant expiresAt) {
    return Link.builder()
        .id(UUID.randomUUID())
        .code(code)
        .originalUrl(URL)
        .urlHash("hash")
        .expiresAt(expiresAt)
        .build();
  }
}
