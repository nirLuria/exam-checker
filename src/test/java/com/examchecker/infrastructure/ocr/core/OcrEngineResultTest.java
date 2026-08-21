package com.examchecker.infrastructure.ocr.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrEngineResultTest {

    private static final OcrEngineMetadata METADATA = new OcrEngineMetadata(
            OcrEngineName.GEMINI,
            "gemini-test",
            "adapter-v1"
    );

    @Test
    void successPreservesEvidenceAndMetadataWithoutInventingConfidence() {
        OcrBundleResult bundle = bundle();

        OcrEngineResult result = OcrEngineResult.success(
                METADATA,
                bundle,
                "raw-json",
                null,
                12
        );

        assertTrue(result.succeeded());
        assertFalse(result.failed());
        assertEquals(OcrEngineName.GEMINI, result.engineName());
        assertEquals("gemini-test", result.engineVersion());
        assertEquals("adapter-v1", result.adapterVersion());
        assertEquals("ocr-engine-result-v1", result.contractVersion());
        assertEquals("raw-json", result.rawOutput());
        assertEquals(bundle, result.bundle());
        assertNull(result.reportedConfidence());
        assertEquals(OcrEngineFailureType.NONE, result.failureType());
    }

    @Test
    void failurePreservesRawOutputAndReason() {
        OcrEngineResult result = OcrEngineResult.failed(
                METADATA,
                OcrEngineFailureType.PARSE_ERROR,
                "invalid-json",
                "Failed to parse",
                4
        );

        assertTrue(result.failed());
        assertNull(result.bundle());
        assertEquals("invalid-json", result.rawOutput());
        assertEquals("Failed to parse", result.failureReason());
    }

    @Test
    void rejectsOutOfRangeReportedConfidence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OcrEngineResult.success(METADATA, bundle(), "raw", 1.1, 1)
        );
    }

    private OcrBundleResult bundle() {
        OcrReading reading = new OcrReading("5+3=8", true);
        return new OcrBundleResult(
                reading,
                reading,
                reading,
                new SuspiciousCheckResult(false, "", "")
        );
    }
}
