package com.examchecker.infrastructure.ocr.contract;

public record SuspiciousCheckResult(
        boolean suspicious,
        String reason,
        String suggestedRawText
) {
}
