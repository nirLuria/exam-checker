package com.examchecker.infrastructure.ocr.preparation;

import com.examchecker.image.ImageQualityReport;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record QuestionPackage(
        String contractVersion,
        UUID traceId,
        Instant createdAt,
        QuestionReference reference,
        QuestionImage image,
        QuestionImageQuality imageQuality,
        OcrContext ocrContext
) {
    public static final String CURRENT_CONTRACT_VERSION = "question-package-v1";

    public QuestionPackage {
        if (!CURRENT_CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("Unsupported contractVersion: " + contractVersion);
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(image, "image must not be null");
        Objects.requireNonNull(imageQuality, "imageQuality must not be null");
        Objects.requireNonNull(ocrContext, "ocrContext must not be null");
    }

    public static QuestionPackage create(
            UUID traceId,
            Instant createdAt,
            QuestionReference reference,
            QuestionImage image,
            ImageQualityReport imageQualityReport,
            OcrContext ocrContext
    ) {
        return new QuestionPackage(
                CURRENT_CONTRACT_VERSION,
                traceId,
                createdAt,
                reference,
                image,
                QuestionImageQuality.from(imageQualityReport),
                ocrContext
        );
    }
}
