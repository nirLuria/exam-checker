package com.examchecker.infrastructure.ocr;

public record SuspiciousCheckResult(
        boolean suspicious,
        String reason,
        String suggestedRawText
) {
}