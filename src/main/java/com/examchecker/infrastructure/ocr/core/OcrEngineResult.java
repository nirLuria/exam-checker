package com.examchecker.infrastructure.ocr.core;

public record OcrEngineResult(
        OcrEngineName engineName,
        OcrEngineStatus status,
        OcrEngineFailureType failureType,
        OcrBundleResult bundleResult,
        String errorMessage,
        long durationMs
) {

    public static OcrEngineResult success(
            OcrEngineName engineName,
            OcrBundleResult bundleResult,
            long durationMs
    ) {
        return new OcrEngineResult(
                engineName,
                OcrEngineStatus.SUCCESS,
                OcrEngineFailureType.NONE,
                bundleResult,
                null,
                durationMs
        );
    }

    public static OcrEngineResult failed(
            OcrEngineName engineName,
            OcrEngineFailureType failureType,
            String errorMessage,
            long durationMs
    ) {
        return new OcrEngineResult(
                engineName,
                OcrEngineStatus.FAILED,
                failureType,
                null,
                errorMessage,
                durationMs
        );
    }

    public boolean succeeded() {
        return status == OcrEngineStatus.SUCCESS;
    }

    public boolean failed() {
        return status == OcrEngineStatus.FAILED;
    }
}