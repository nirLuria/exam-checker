package com.examchecker.infrastructure;

public interface OcrService {
    String extractText(byte[] imageContent, String contentType);

    default String modelVersion() {
        return "unspecified";
    }
}
