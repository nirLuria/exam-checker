package com.examchecker.infrastructure.ocr.core;

public record OcrReading(
        String rawText,
        boolean clearlyReadable
) {
}