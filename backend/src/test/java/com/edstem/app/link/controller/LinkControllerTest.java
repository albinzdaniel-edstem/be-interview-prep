package com.edstem.app.link.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.app.common.exception.GlobalExceptionHandler;
import com.edstem.app.link.dto.request.ShortenUrlRequest;
import com.edstem.app.link.dto.response.LinkResponse;
import com.edstem.app.link.dto.response.LinkStatsResponse;
import com.edstem.app.link.dto.response.ShortenResult;
import com.edstem.app.link.exception.LinkExpiredException;
import com.edstem.app.link.exception.LinkNotFoundException;
import com.edstem.app.link.service.LinkService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({LinkController.class, RedirectController.class})
@Import(GlobalExceptionHandler.class)
class LinkControllerTest {

  private static final String LINKS_URL = "/api/v1/links";
  private static final Instant CREATED = Instant.parse("2030-01-01T10:00:00Z");

  @Autowired private MockMvc mvc;
  @MockitoBean private LinkService linkService;

  @Test
  void shortenReturns201WithTheShortCodeAndUrl() throws Exception {
    LinkResponse link =
        new LinkResponse(
            "abc12345", "http://short.test/s/abc12345", "https://example.com/page", null, CREATED);
    when(linkService.shorten(any(ShortenUrlRequest.class)))
        .thenReturn(new ShortenResult(link, true));
    String body = "{\"url\":\"https://example.com/page\"}";

    mvc.perform(post(LINKS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.code").value("abc12345"))
        .andExpect(jsonPath("$.data.shortUrl").value("http://short.test/s/abc12345"))
        .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/page"));
  }

  @Test
  void shortenReturns200WhenTheUrlWasShortenedBefore() throws Exception {
    LinkResponse link =
        new LinkResponse(
            "abc12345", "http://short.test/s/abc12345", "https://example.com/page", null, CREATED);
    when(linkService.shorten(any(ShortenUrlRequest.class)))
        .thenReturn(new ShortenResult(link, false));
    String body = "{\"url\":\"https://example.com/page\"}";

    mvc.perform(post(LINKS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("abc12345"));
  }

  @Test
  void shortenPassesTheExpiryToTheService() throws Exception {
    LinkResponse link =
        new LinkResponse(
            "abc12345", "http://short.test/s/abc12345", "https://e.com", null, CREATED);
    when(linkService.shorten(any(ShortenUrlRequest.class)))
        .thenReturn(new ShortenResult(link, true));
    String body = "{\"url\":\"https://e.com\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}";

    mvc.perform(post(LINKS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    ArgumentCaptor<ShortenUrlRequest> request = ArgumentCaptor.forClass(ShortenUrlRequest.class);
    verify(linkService).shorten(request.capture());
    assertThat(request.getValue().expiresAt()).isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
  }

  @Test
  void shortenRejectsAnInvalidUrl() throws Exception {
    String body = "{\"url\":\"not a url\"}";

    mvc.perform(post(LINKS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("url"))
        .andExpect(
            jsonPath("$.error.fieldErrors[0].message").value("Enter a valid http or https URL"));
  }

  @Test
  void shortenRejectsAScriptUrl() throws Exception {
    String body = "{\"url\":\"javascript:alert(1)\"}";

    mvc.perform(post(LINKS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("url"));
  }

  @Test
  void shortenReportsEveryInvalidField() throws Exception {
    String body = "{\"url\":\"\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}";

    mvc.perform(post(LINKS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='url')].message")
                .value(hasItem("URL is required")))
        .andExpect(
            jsonPath("$.error.fieldErrors[?(@.field=='expiresAt')].message")
                .value(hasItem("Expiry must be in the future")));
  }

  @Test
  void shortenRejectsAnUnreadableExpiry() throws Exception {
    String body = "{\"url\":\"https://e.com\",\"expiresAt\":\"tomorrow\"}";

    mvc.perform(post(LINKS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("expiresAt"));
  }

  @Test
  void redirectSendsA302WithoutCaching() throws Exception {
    when(linkService.visit("abc12345")).thenReturn("https://example.com/page?x=1");

    mvc.perform(get("/s/abc12345"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/page?x=1"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().string(""));
  }

  @Test
  void redirectReturns404ForAnUnknownCode() throws Exception {
    when(linkService.visit("nope")).thenThrow(new LinkNotFoundException("nope"));

    mvc.perform(get("/s/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("LINK_NOT_FOUND"))
        .andExpect(jsonPath("$.error.status").value(404));
  }

  @Test
  void redirectReturns410ForAnExpiredCode() throws Exception {
    when(linkService.visit("old12345")).thenThrow(new LinkExpiredException("old12345"));

    mvc.perform(get("/s/old12345"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.error.code").value("LINK_EXPIRED"))
        .andExpect(jsonPath("$.error.status").value(410));
  }

  @Test
  void statsShowTheOriginalUrlVisitCountAndCreatedDate() throws Exception {
    LinkStatsResponse stats =
        new LinkStatsResponse("abc12345", "https://example.com/page", 42, CREATED, null);
    when(linkService.stats("abc12345")).thenReturn(stats);

    mvc.perform(get(LINKS_URL + "/abc12345/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("abc12345"))
        .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/page"))
        .andExpect(jsonPath("$.data.visitCount").value(42))
        .andExpect(jsonPath("$.data.createdAt").value("2030-01-01T10:00:00Z"));
  }

  @Test
  void statsReturn404ForAnUnknownCode() throws Exception {
    when(linkService.stats("nope")).thenThrow(new LinkNotFoundException("nope"));

    mvc.perform(get(LINKS_URL + "/nope/stats"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("LINK_NOT_FOUND"));
  }
}
