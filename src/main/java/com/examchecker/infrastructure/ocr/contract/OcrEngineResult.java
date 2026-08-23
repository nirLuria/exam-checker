package com.examchecker.infrastructure.ocr.contract;

import java.util.List;
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
        Double derivedConfidence,
        List<OcrEvidence> evidence,
        String failureReason,
        OcrRunMetadata runMetadata
) {

    public static final String CURRENT_CONTRACT_VERSION = "ocr-engine-result-v2";

    public OcrEngineResult {
        Objects.requireNonNull(engineName, "engineName must not be null");
        engineVersion = requireText(engineVersion, "engineVersion");
        adapterVersion = requireText(adapterVersion, "adapterVersion");
        if (!CURRENT_CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("Unsupported contractVersion: " + contractVersion);
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(failureType, "failureType must not be null");
        rawOutput = rawOutput == null ? "" : rawOutput;
        validateConfidence(reportedConfidence, "reportedConfidence");
        validateConfidence(derivedConfidence, "derivedConfidence");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        failureReason = failureReason == null ? "" : failureReason.trim();
        Objects.requireNonNull(runMetadata, "runMetadata must not be null");

        if (status == OcrEngineStatus.SUCCESS) {
            Objects.requireNonNull(bundle, "Successful OCR result must contain a bundle");
            if (failureType != OcrEngineFailureType.NONE || !failureReason.isEmpty()) {
                throw new IllegalArgumentException("Successful OCR result cannot contain failure details");
            }
        } else {
            if (bundle != null) {
                throw new IllegalArgumentException("Non-success OCR result cannot contain a bundle");
            }
            validateNonSuccess(status, failureType);
        }
    }

    public static OcrEngineResult success(
            OcrEngineMetadata metadata,
            OcrRunMetadata runMetadata,
            OcrBundleResult bundle,
            String rawOutput,
            Double reportedConfidence,
            Double derivedConfidence,
            List<OcrEvidence> evidence
    ) {
        return create(
                metadata, runMetadata, OcrEngineStatus.SUCCESS, OcrEngineFailureType.NONE,
                bundle, rawOutput, reportedConfidence, derivedConfidence, evidence, ""
        );
    }

    public static OcrEngineResult failed(
            OcrEngineMetadata metadata,
            OcrRunMetadata runMetadata,
            OcrEngineFailureType failureType,
            String rawOutput,
            String failureReason
    ) {
        OcrEngineStatus status = failureType == OcrEngineFailureType.TIMEOUT
                ? OcrEngineStatus.TIMEOUT
                : OcrEngineStatus.FAILED;
        return create(
                metadata, runMetadata, status, failureType,
                null, rawOutput, null, null, List.of(), failureReason
        );
    }

    public static OcrEngineResult notApplicable(
            OcrEngineMetadata metadata,
            OcrRunMetadata runMetadata,
            String reason
    ) {
        return create(
                metadata, runMetadata, OcrEngineStatus.NOT_APPLICABLE, OcrEngineFailureType.NONE,
                null, "", null, null, List.of(), reason
        );
    }

    public boolean succeeded() {
        return status == OcrEngineStatus.SUCCESS;
    }

    public boolean failed() {
        return status == OcrEngineStatus.FAILED || status == OcrEngineStatus.TIMEOUT;
    }

    public boolean notApplicable() {
        return status == OcrEngineStatus.NOT_APPLICABLE;
    }

    private static OcrEngineResult create(
            OcrEngineMetadata metadata,
            OcrRunMetadata runMetadata,
            OcrEngineStatus status,
            OcrEngineFailureType failureType,
            OcrBundleResult bundle,
            String rawOutput,
            Double reportedConfidence,
            Double derivedConfidence,
            List<OcrEvidence> evidence,
            String failureReason
    ) {
        return new OcrEngineResult(
                metadata.engineName(), metadata.engineVersion(), metadata.adapterVersion(),
                CURRENT_CONTRACT_VERSION, status, failureType, bundle, rawOutput,
                reportedConfidence, derivedConfidence, evidence, failureReason, runMetadata
        );
    }

    private static void validateNonSuccess(
            OcrEngineStatus status,
            OcrEngineFailureType failureType
    ) {
        if (status == OcrEngineStatus.NOT_APPLICABLE) {
            if (failureType != OcrEngineFailureType.NONE) {
                throw new IllegalArgumentException("NOT_APPLICABLE is not an engine failure");
            }
            return;
        }
        if (failureType == OcrEngineFailureType.NONE) {
            throw new IllegalArgumentException("Failed OCR result must contain a failure type");
        }
        if (status == OcrEngineStatus.TIMEOUT && failureType != OcrEngineFailureType.TIMEOUT) {
            throw new IllegalArgumentException("TIMEOUT status requires TIMEOUT failure type");
        }
    }

    private static void validateConfidence(Double confidence, String fieldName) {
        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
