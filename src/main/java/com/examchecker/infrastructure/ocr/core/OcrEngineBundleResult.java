package com.examchecker.infrastructure.ocr.core;

public record OcrEngineBundleResult(
        OcrEngineName engineName,
        OcrBundleResult bundle,
        boolean failed,
        String failureReason
) {
}