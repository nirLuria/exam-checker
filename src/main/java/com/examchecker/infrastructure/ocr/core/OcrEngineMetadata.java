package com.examchecker.infrastructure.ocr.core;

import java.util.Objects;

public record OcrEngineMetadata(
        OcrEngineName engineName,
        String engineVersion,
        String adapterVersion
) {

    public OcrEngineMetadata {
        Objects.requireNonNull(engineName, "engineName must not be null");
        engineVersion = requireText(engineVersion, "engineVersion");
        adapterVersion = requireText(adapterVersion, "adapterVersion");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
