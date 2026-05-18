package com.examchecker.infrastructure.ocr.core;

public record SuspiciousCheckResult(
        boolean suspicious,
        String reason,
        String suggestedRawText
) {
}