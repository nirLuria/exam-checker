package com.examchecker.infrastructure.ocr.contract;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrEngineResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final OcrEngineMetadata METADATA = new OcrEngineMetadata(
            OcrEngineName.GEMINI,
            "gemini-test",
            "adapter-v1"
    );

    @Test
    void successPreservesTraceEvidenceAndSeparateConfidenceTypes() {
        OcrBundleResult bundle = bundle();
        OcrEvidence evidence = new OcrEvidence(
                OcrEvidenceType.PRIMARY_READING,
                "Primary reading",
                Map.of("rawText", "5+3=8")
        );

        OcrEngineResult result = OcrEngineResult.success(
                METADATA,
                runMetadata(),
                bundle,
                "raw-json",
                0.91,
                0.84,
                List.of(evidence)
        );

        assertTrue(result.succeeded());
        assertFalse(result.failed());
        assertEquals(OcrEngineName.GEMINI, result.engineName());
        assertEquals("gemini-test", result.engineVersion());
        assertEquals("adapter-v1", result.adapterVersion());
        assertEquals("ocr-engine-result-v2", result.contractVersion());
        assertEquals("raw-json", result.rawOutput());
        assertEquals(bundle, result.bundle());
        assertEquals(0.91, result.reportedConfidence());
        assertEquals(0.84, result.derivedConfidence());
        assertEquals(List.of(evidence), result.evidence());
        assertEquals(runMetadata().traceId(), result.runMetadata().traceId());
        assertEquals(OcrEngineFailureType.NONE, result.failureType());
    }

    @Test
    void timeoutHasExplicitStatusAndPreservesReason() {
        OcrEngineResult result = OcrEngineResult.failed(
                METADATA,
                runMetadata(),
                OcrEngineFailureType.TIMEOUT,
                "partial-output",
                "request timed out"
        );

        assertTrue(result.failed());
        assertEquals(OcrEngineStatus.TIMEOUT, result.status());
        assertNull(result.bundle());
        assertEquals("partial-output", result.rawOutput());
        assertEquals("request timed out", result.failureReason());
    }

    @Test
    void notApplicableIsNotCountedAsEngineFailure() {
        OcrEngineResult result = OcrEngineResult.notApplicable(
                METADATA,
                runMetadata(),
                "Engine does not support this question type"
        );

        assertTrue(result.notApplicable());
        assertFalse(result.failed());
        assertEquals(OcrEngineFailureType.NONE, result.failureType());
    }

    @Test
    void rejectsOutOfRangeConfidenceIndependently() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OcrEngineResult.success(
                        METADATA, runMetadata(), bundle(), "raw", 1.1, null, List.of()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> OcrEngineResult.success(
                        METADATA, runMetadata(), bundle(), "raw", null, -0.1, List.of()
                )
        );
    }

    @Test
    void retryMustReferenceOriginalIndependentRun() {
        UUID runId = UUID.fromString("940c7f5e-32aa-44a5-9c40-fca3e277401f");
        Instant timestamp = Instant.parse("2026-08-23T08:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> new OcrRunMetadata(
                        traceId(), runId, 2, true, runId, timestamp, timestamp, 1
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OcrRunMetadata(
                        traceId(), UUID.randomUUID(), 2, false, null, timestamp, timestamp, 1
                )
        );
    }

    @Test
    void jsonRoundTripPreservesAuditContract() throws Exception {
        OcrEngineResult original = OcrEngineResult.success(
                METADATA,
                runMetadata(),
                bundle(),
                "raw-json",
                0.91,
                null,
                List.of(new OcrEvidence(
                        OcrEvidenceType.PRIMARY_READING,
                        "Primary reading",
                        Map.of("rawText", "5+3=8")
                ))
        );

        String json = objectMapper.writeValueAsString(original);
        OcrEngineResult restored = objectMapper.readValue(json, OcrEngineResult.class);

        assertEquals(original, restored);
    }

    private OcrRunMetadata runMetadata() {
        Instant timestamp = Instant.parse("2026-08-23T08:00:00Z");
        return OcrRunMetadata.firstAttempt(
                traceId(),
                UUID.fromString("940c7f5e-32aa-44a5-9c40-fca3e277401f"),
                timestamp,
                timestamp,
                12
        );
    }

    private UUID traceId() {
        return UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
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
