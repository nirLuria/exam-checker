package com.examchecker.infrastructure.ocr.decision;

import com.examchecker.infrastructure.ocr.postprocessing.OcrEngineTextEvidence;

import java.util.List;

public record OcrComparisonResult(
        int successfulEngineCount,
        boolean unanimousTextAgreement,
        boolean unanimousOperatorAgreement,
        List<String> distinctCanonicalTexts,
        List<OcrEngineTextEvidence> evidence
) {

    public OcrComparisonResult {
        if (successfulEngineCount < 0) {
            throw new IllegalArgumentException("successfulEngineCount must not be negative");
        }
        distinctCanonicalTexts = List.copyOf(distinctCanonicalTexts);
        evidence = List.copyOf(evidence);
        if (successfulEngineCount != evidence.size()) {
            throw new IllegalArgumentException("successfulEngineCount must match evidence size");
        }
    }
}
