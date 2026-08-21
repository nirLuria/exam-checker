package com.examchecker.infrastructure.ocr.core;

import com.examchecker.service.MathNormalizationResult;

import java.util.Objects;

public record OcrEngineTextEvidence(
        OcrEngineName engineName,
        String rawText,
        MathNormalizationResult normalization,
        String operatorSignature
) {

    public OcrEngineTextEvidence {
        Objects.requireNonNull(engineName, "engineName must not be null");
        rawText = rawText == null ? "" : rawText;
        Objects.requireNonNull(normalization, "normalization must not be null");
        operatorSignature = operatorSignature == null ? "" : operatorSignature;
    }
}
