package com.edstem.app.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.edstem.app.link.dto.request.ShortenUrlRequest;
import com.edstem.app.link.repository.LinkRepository;
import com.edstem.app.link.service.LinkService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LinkConcurrencyTest {

  private static final int THREADS = 50;

  @Autowired private LinkService linkService;
  @Autowired private LinkRepository linkRepository;

  @Test
  void visitCountStaysAccurateWhenManyPeopleOpenTheSameLinkAtOnce() throws Exception {
    String code =
        linkService.shorten(new ShortenUrlRequest("https://example.com/busy", null)).link().code();

    runAtTheSameTime(THREADS, () -> linkService.visit(code));

    long visits = linkRepository.findByCode(code).orElseThrow().getVisitCount();
    assertThat(visits).isEqualTo(THREADS);
  }

  @Test
  void shorteningTheSameUrlAtOnceCreatesOnlyOneLink() throws Exception {
    String url = "https://example.com/shared";
    ShortenUrlRequest request = new ShortenUrlRequest(url, null);

    List<String> codes =
        runAtTheSameTime(THREADS, () -> linkService.shorten(request).link().code());

    assertThat(new HashSet<>(codes)).hasSize(1);
    long rows =
        linkRepository.findAll().stream().filter(l -> url.equals(l.getOriginalUrl())).count();
    assertThat(rows).isEqualTo(1);
  }

  private static <T> List<T> runAtTheSameTime(int threads, Callable<T> task) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return task.call();
                }));
      }
      ready.await();
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }
}
