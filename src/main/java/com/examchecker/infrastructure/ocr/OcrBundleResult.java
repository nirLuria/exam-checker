package com.examchecker.infrastructure.ocr;

public record OcrBundleResult(
        OcrReading primary,
        OcrReading verification,
        OcrReading thresholdRead,
        SuspiciousCheckResult suspiciousCheck
) {
}