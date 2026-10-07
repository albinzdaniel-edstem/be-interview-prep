package com.edstem.app.link.mapper;

import com.edstem.app.link.config.LinkProperties;
import com.edstem.app.link.dto.response.LinkResponse;
import com.edstem.app.link.dto.response.LinkStatsResponse;
import com.edstem.app.link.entity.Link;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkMapper {

  private static final String VISIT_PATH = "/s/";

  private final LinkProperties properties;

  public LinkResponse toResponse(Link link) {
    return new LinkResponse(
        link.getCode(),
        shortUrl(link.getCode()),
        link.getOriginalUrl(),
        link.getExpiresAt(),
        link.getCreatedAt());
  }

  public LinkStatsResponse toStats(Link link) {
    return new LinkStatsResponse(
        link.getCode(),
        link.getOriginalUrl(),
        link.getVisitCount(),
        link.getCreatedAt(),
        link.getExpiresAt());
  }

  private String shortUrl(String code) {
    String base = properties.baseUrl();
    String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return trimmed + VISIT_PATH + code;
  }
}
