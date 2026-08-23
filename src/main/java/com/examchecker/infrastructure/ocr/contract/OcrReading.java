package com.examchecker.infrastructure.ocr.contract;

public record OcrReading(
        String rawText,
        boolean clearlyReadable
) {
}
