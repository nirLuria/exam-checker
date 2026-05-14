package com.examchecker.infrastructure.ocr;

public record OcrEngineBundleResult(
        OcrEngineName engineName,
        OcrBundleResult bundle,
        boolean failed,
        String failureReason
) {
}