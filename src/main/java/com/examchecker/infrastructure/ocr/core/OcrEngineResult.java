package com.examchecker.infrastructure.ocr.core;

import java.util.Objects;

public record OcrEngineResult(
        OcrEngineName engineName,
        String engineVersion,
        String adapterVersion,
        String contractVersion,
        OcrEngineStatus status,
        OcrEngineFailureType failureType,
        OcrBundleResult bundle,
        String rawOutput,
        Double reportedConfidence,
        String failureReason,
        long durationMs
) {

    public static final String CURRENT_CONTRACT_VERSION = "ocr-engine-result-v1";

    public OcrEngineResult {
        Objects.requireNonNull(engineName, "engineName must not be null");
        engineVersion = requireText(engineVersion, "engineVersion");
        adapterVersion = requireText(adapterVersion, "adapterVersion");
        contractVersion = requireText(contractVersion, "contractVersion");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(failureType, "failureType must not be null");
        rawOutput = rawOutput == null ? "" : rawOutput;
        failureReason = failureReason == null ? "" : failureReason;

        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (reportedConfidence != null
                && (reportedConfidence < 0.0 || reportedConfidence > 1.0)) {
            throw new IllegalArgumentException("reportedConfidence must be between 0 and 1");
        }
        if (status == OcrEngineStatus.SUCCESS) {
            Objects.requireNonNull(bundle, "Successful OCR result must contain a bundle");
            if (failureType != OcrEngineFailureType.NONE) {
                throw new IllegalArgumentException("Successful OCR result cannot contain a failure type");
            }
        } else {
            if (bundle != null) {
                throw new IllegalArgumentException("Failed OCR result cannot contain a bundle");
            }
            if (failureType == OcrEngineFailureType.NONE) {
                throw new IllegalArgumentException("Failed OCR result must contain a failure type");
            }
        }
    }

    public static OcrEngineResult success(
            OcrEngineMetadata metadata,
            OcrBundleResult bundle,
            String rawOutput,
            Double reportedConfidence,
            long durationMs
    ) {
        return new OcrEngineResult(
                metadata.engineName(),
                metadata.engineVersion(),
                metadata.adapterVersion(),
                CURRENT_CONTRACT_VERSION,
                OcrEngineStatus.SUCCESS,
                OcrEngineFailureType.NONE,
                bundle,
                rawOutput,
                reportedConfidence,
                "",
                durationMs
        );
    }

    public static OcrEngineResult failed(
            OcrEngineMetadata metadata,
            OcrEngineFailureType failureType,
            String rawOutput,
            String failureReason,
            long durationMs
    ) {
        return new OcrEngineResult(
                metadata.engineName(),
                metadata.engineVersion(),
                metadata.adapterVersion(),
                CURRENT_CONTRACT_VERSION,
                OcrEngineStatus.FAILED,
                failureType,
                null,
                rawOutput,
                null,
                failureReason,
                durationMs
        );
    }

    public boolean succeeded() {
        return status == OcrEngineStatus.SUCCESS;
    }

    public boolean failed() {
        return status == OcrEngineStatus.FAILED;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
