package com.examchecker.image;

public record ImageQualityReport(
        boolean tooSmall,
        boolean blurry,
        boolean lowContrast,
        boolean tooDark,
        boolean tooBright,
        boolean suspicious,
        String reason,
        double blurScore,
        double contrastScore,
        double brightnessScore
) {
}