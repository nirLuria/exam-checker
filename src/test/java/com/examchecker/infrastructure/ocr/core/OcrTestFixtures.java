package com.examchecker.infrastructure.ocr.core;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
                runMetadata(),
                bundle,
                "raw-" + engineName,
                null,
                null,
                List.of()
        );
    }

    static OcrEngineResult failure(OcrEngineName engineName) {
        return OcrEngineResult.failed(
                metadata(engineName),
                runMetadata(),
                OcrEngineFailureType.TIMEOUT,
                "",
                "timed out"
        );
    }

    static OcrEngineResult notApplicable(OcrEngineName engineName) {
        return OcrEngineResult.notApplicable(
                metadata(engineName),
                runMetadata(),
                "unsupported question type"
        );
    }

    static OcrRunMetadata runMetadata() {
        Instant timestamp = Instant.parse("2026-08-23T08:00:00Z");
        return OcrRunMetadata.firstAttempt(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.randomUUID(),
                timestamp,
                timestamp,
                1
        );
    }

    private static OcrEngineMetadata metadata(OcrEngineName engineName) {
        return new OcrEngineMetadata(engineName, "test-model", "test-adapter-v1");
    }
}
