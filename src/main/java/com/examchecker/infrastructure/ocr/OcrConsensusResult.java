package com.examchecker.infrastructure.ocr;

import java.util.List;

public record OcrConsensusResult(
        OcrBundleResult selectedBundle,
        List<OcrEngineBundleResult> engineResults,
        boolean needsReview,
        String reason
) {
}