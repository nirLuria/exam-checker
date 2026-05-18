package com.examchecker.infrastructure.ocr.core;

public record OcrBundleResult(
        OcrReading primary,
        OcrReading verification,
        OcrReading thresholdRead,
        SuspiciousCheckResult suspiciousCheck
) {
}