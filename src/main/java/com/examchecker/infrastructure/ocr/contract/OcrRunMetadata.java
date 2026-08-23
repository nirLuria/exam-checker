package com.examchecker.infrastructure.ocr.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OcrRunMetadata(
        UUID traceId,
        UUID runId,
        int attemptNumber,
        boolean retry,
        UUID originalRunId,
        Instant startedAt,
        Instant completedAt,
        long durationMs
) {
    public OcrRunMetadata {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        if (attemptNumber == 1 && (retry || originalRunId != null)) {
            throw new IllegalArgumentException("First attempt cannot be marked as a retry");
        }
        if (attemptNumber > 1 && (!retry || originalRunId == null)) {
            throw new IllegalArgumentException("Retry attempts must reference the original run");
        }
        if (runId.equals(originalRunId)) {
            throw new IllegalArgumentException("A retry run cannot reference itself");
        }
    }

    public static OcrRunMetadata firstAttempt(
            UUID traceId,
            UUID runId,
            Instant startedAt,
            Instant completedAt,
            long durationMs
    ) {
        return new OcrRunMetadata(
                traceId, runId, 1, false, null, startedAt, completedAt, durationMs
        );
    }
}
