package com.ivs.app.web;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        String code,
        String message,
        Object details
) {
}