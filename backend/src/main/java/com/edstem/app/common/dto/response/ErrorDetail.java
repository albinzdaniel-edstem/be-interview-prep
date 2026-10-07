package com.edstem.app.common.dto.response;

import java.time.Instant;
import java.util.List;

public record ErrorDetail(
    String code, int status, Instant timestamp, String path, List<FieldErrorDetail> fieldErrors) {}
