package com.examchecker.infrastructure.ocr;

public record OcrReading(
        String rawText,
        boolean clearlyReadable
) {
}