package com.edstem.app.link.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edstem.app.link.entity.Link;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class LinkRepositoryTest {

  @Autowired private LinkRepository linkRepository;

  @Test
  void savesLinkWithGeneratedIdAndCreatedDate() {
    Link saved = linkRepository.saveAndFlush(newLink("abc12345", "hash-1"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getVisitCount()).isZero();
  }

  @Test
  void findsALinkByCodeAndByUrlHash() {
    linkRepository.saveAndFlush(newLink("abc12345", "hash-1"));

    assertThat(linkRepository.findByCode("abc12345")).isPresent();
    assertThat(linkRepository.findByUrlHash("hash-1")).isPresent();
    assertThat(linkRepository.findByCode("missing")).isEmpty();
    assertThat(linkRepository.findByUrlHash("missing")).isEmpty();
  }

  @Test
  void rejectsADuplicateCode() {
    linkRepository.saveAndFlush(newLink("abc12345", "hash-1"));
    Link duplicate = newLink("abc12345", "hash-2");

    assertThatThrownBy(() -> linkRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsADuplicateUrlHash() {
    linkRepository.saveAndFlush(newLink("abc12345", "hash-1"));
    Link duplicate = newLink("zzz99999", "hash-1");

    assertThatThrownBy(() -> linkRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void incrementsTheVisitCountInTheDatabase() {
    Link saved = linkRepository.saveAndFlush(newLink("abc12345", "hash-1"));

    int firstUpdate = linkRepository.incrementVisitCount(saved.getId());
    int secondUpdate = linkRepository.incrementVisitCount(saved.getId());

    assertThat(firstUpdate).isEqualTo(1);
    assertThat(secondUpdate).isEqualTo(1);
    assertThat(linkRepository.findById(saved.getId()).orElseThrow().getVisitCount()).isEqualTo(2);
  }

  @Test
  void incrementingAnUnknownLinkChangesNothing() {
    int updated = linkRepository.incrementVisitCount(UUID.randomUUID());

    assertThat(updated).isZero();
  }

  private static Link newLink(String code, String urlHash) {
    return Link.builder()
        .code(code)
        .originalUrl("https://example.com/" + code)
        .urlHash(urlHash)
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }
}
