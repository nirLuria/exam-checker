package com.examchecker.infrastructure.ocr.core;

import java.util.List;

public record OcrConsensusResult(
        OcrBundleResult selectedBundle,
        List<OcrEngineBundleResult> engineResults,
        boolean needsReview,
        String reason
) {
}