package com.examchecker.image;

import org.springframework.stereotype.Component;

@Component
public class ImageQualityPolicy {

    private static final String VERSION = "image-quality-v2";

    private static final int MIN_WIDTH = 180;
    private static final int MIN_HEIGHT = 60;

    private static final double BLUR_THRESHOLD = 80.0;
    private static final double LOW_CONTRAST_THRESHOLD = 35.0;
    private static final double TOO_DARK_THRESHOLD = 45.0;
    private static final double TOO_BRIGHT_THRESHOLD = 220.0;

    private static final double TOO_SMALL_PENALTY = 30.0;
    private static final double BLUR_PENALTY = 25.0;
    private static final double LOW_CONTRAST_PENALTY = 20.0;
    private static final double EXTREME_BRIGHTNESS_PENALTY = 25.0;

    private static final double ACCEPT_THRESHOLD = 80.0;
    private static final double REVIEW_THRESHOLD = 50.0;

    public String version() {
        return VERSION;
    }

    public boolean isTooSmall(int width, int height) {
        return width < MIN_WIDTH || height < MIN_HEIGHT;
    }

    public boolean isBlurry(double blurScore) {
        return blurScore < BLUR_THRESHOLD;
    }

    public boolean isLowContrast(double contrastScore) {
        return contrastScore < LOW_CONTRAST_THRESHOLD;
    }

    public boolean isTooDark(double brightnessScore) {
        return brightnessScore < TOO_DARK_THRESHOLD;
    }

    public boolean isTooBright(double brightnessScore) {
        return brightnessScore > TOO_BRIGHT_THRESHOLD;
    }

    public double calculateScore(
            boolean tooSmall,
            boolean blurry,
            boolean lowContrast,
            boolean tooDark,
            boolean tooBright
    ) {
        double score = 100.0;

        if (tooSmall) {
            score -= TOO_SMALL_PENALTY;
        }
        if (blurry) {
            score -= BLUR_PENALTY;
        }
        if (lowContrast) {
            score -= LOW_CONTRAST_PENALTY;
        }
        if (tooDark || tooBright) {
            score -= EXTREME_BRIGHTNESS_PENALTY;
        }

        return Math.max(0.0, score);
    }

    public ImageQualityDecision decide(double score) {
        if (score >= ACCEPT_THRESHOLD) {
            return ImageQualityDecision.ACCEPT;
        }
        if (score >= REVIEW_THRESHOLD) {
            return ImageQualityDecision.REVIEW;
        }
        return ImageQualityDecision.REJECT;
    }
}
