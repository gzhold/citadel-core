package io.citadel.core.dto;

import java.time.Instant;

/** 统一错误响应体：{ code, message, timestamp, errors? } */
public record ErrorResponse(String code, String message, Instant timestamp) {}
