package com.examchecker.infrastructure.ocr.core;

final class OcrTestFixtures {

    private OcrTestFixtures() {
    }

    static OcrEngineResult success(OcrEngineName engineName, String text) {
        OcrReading reading = new OcrReading(text, true);
        OcrBundleResult bundle = new OcrBundleResult(
                reading,
                reading,
                reading,
                new SuspiciousCheckResult(false, "", "")
        );

        return OcrEngineResult.success(
                metadata(engineName),
                bundle,
                "raw-" + engineName,
                null,
                1
        );
    }

    static OcrEngineResult failure(OcrEngineName engineName) {
        return OcrEngineResult.failed(
                metadata(engineName),
                OcrEngineFailureType.TIMEOUT,
                "",
                "timed out",
                1
        );
    }

    private static OcrEngineMetadata metadata(OcrEngineName engineName) {
        return new OcrEngineMetadata(engineName, "test-model", "test-adapter-v1");
    }
}
