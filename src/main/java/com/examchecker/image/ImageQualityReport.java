package com.examchecker.image;

import java.util.List;

public record ImageQualityReport(
        boolean tooSmall,
        boolean blurry,
        boolean lowContrast,
        boolean tooDark,
        boolean tooBright,
        boolean suspicious,
        String reason,
        List<ImageQualityReasonCode> reasonCodes,
        double blurScore,
        double contrastScore,
        double brightnessScore,
        boolean analyzable,
        int width,
        int height,
        double qualityScore,
        ImageQualityDecision decision,
        String policyVersion
) {
    public ImageQualityReport {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
