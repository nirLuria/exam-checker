package com.examchecker.infrastructure.ocr.contract;

public record OcrBundleResult(
        OcrReading primary,
        OcrReading verification,
        OcrReading thresholdRead,
        SuspiciousCheckResult suspiciousCheck
) {
}
