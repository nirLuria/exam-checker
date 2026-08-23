package com.examchecker.infrastructure.ocr.decision;

import com.examchecker.infrastructure.ocr.contract.OcrBundleResult;
import com.examchecker.infrastructure.ocr.contract.OcrEngineName;
import com.examchecker.infrastructure.ocr.contract.OcrEngineResult;

import java.util.List;

public record OcrConsensusResult(
        OcrEngineName selectedEngineName,
        OcrBundleResult selectedBundle,
        List<OcrEngineResult> engineResults,
        OcrComparisonResult comparison,
        boolean needsReview,
        String reason,
        String policyVersion
) {

    public OcrConsensusResult {
        engineResults = List.copyOf(engineResults);
        if (comparison == null) {
            throw new IllegalArgumentException("comparison must not be null");
        }
        reason = reason == null ? "" : reason;
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        if ((selectedEngineName == null) != (selectedBundle == null)) {
            throw new IllegalArgumentException(
                    "selectedEngineName and selectedBundle must either both exist or both be null"
            );
        }
    }
}
