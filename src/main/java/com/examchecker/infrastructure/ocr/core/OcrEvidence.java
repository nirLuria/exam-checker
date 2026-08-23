package com.examchecker.infrastructure.ocr.core;

import java.util.Map;
import java.util.Objects;

public record OcrEvidence(
        OcrEvidenceType type,
        String description,
        Map<String, String> attributes
) {
    public OcrEvidence {
        Objects.requireNonNull(type, "type must not be null");
        description = description == null ? "" : description.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
