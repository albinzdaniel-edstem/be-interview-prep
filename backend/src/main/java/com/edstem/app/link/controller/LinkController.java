package com.edstem.app.link.controller;

import com.edstem.app.common.dto.response.ApiResponse;
import com.edstem.app.link.dto.request.ShortenUrlRequest;
import com.edstem.app.link.dto.response.LinkResponse;
import com.edstem.app.link.dto.response.LinkStatsResponse;
import com.edstem.app.link.dto.response.ShortenResult;
import com.edstem.app.link.service.LinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkController {

  private final LinkService linkService;

  @PostMapping
  public ResponseEntity<ApiResponse<LinkResponse>> shorten(
      @Valid @RequestBody ShortenUrlRequest request) {
    ShortenResult result = linkService.shorten(request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.ok(result.link()));
  }

  @GetMapping("/{code}/stats")
  public ApiResponse<LinkStatsResponse> stats(@PathVariable String code) {
    return ApiResponse.ok(linkService.stats(code));
  }
}
