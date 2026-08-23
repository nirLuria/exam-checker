package com.examchecker.question;

import com.examchecker.image.ImageQualityDecision;
import com.examchecker.image.ImageQualityReasonCode;
import com.examchecker.image.ImageQualityReport;

import java.util.List;
import java.util.Objects;

public record QuestionImageQuality(
        double score,
        ImageQualityDecision decision,
        List<ImageQualityReasonCode> reasonCodes,
        String policyVersion
) {
    public QuestionImageQuality {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        Objects.requireNonNull(decision, "decision must not be null");
        reasonCodes = List.copyOf(reasonCodes);
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        policyVersion = policyVersion.trim();
    }

    public static QuestionImageQuality from(ImageQualityReport report) {
        Objects.requireNonNull(report, "report must not be null");
        return new QuestionImageQuality(
                report.qualityScore(),
                report.decision(),
                report.reasonCodes(),
                report.policyVersion()
        );
    }
}
