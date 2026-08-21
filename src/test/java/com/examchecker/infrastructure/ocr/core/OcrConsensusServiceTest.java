package com.examchecker.infrastructure.ocr.core;

import com.examchecker.service.CanonicalMathNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrConsensusServiceTest {

    private final OcrConsensusService service = new OcrConsensusService(
            new OcrResultComparisonService(new CanonicalMathNormalizer())
    );

    @Test
    void emptyResultsRequireReviewWithoutSelection() {
        OcrConsensusResult result = service.decide(List.of());

        assertTrue(result.needsReview());
        assertNull(result.selectedBundle());
        assertEquals("ocr-consensus-v1", result.policyVersion());
    }

    @Test
    void allFailuresRequireReviewWithoutSelection() {
        OcrConsensusResult result = service.decide(List.of(
                OcrTestFixtures.failure(OcrEngineName.OPENAI),
                OcrTestFixtures.failure(OcrEngineName.GEMINI)
        ));

        assertTrue(result.needsReview());
        assertNull(result.selectedEngineName());
        assertEquals("All OCR engines failed", result.reason());
    }

    @Test
    void singleSuccessIsNotIndependentConsensus() {
        OcrConsensusResult result = service.decide(List.of(
                OcrTestFixtures.success(OcrEngineName.OPENAI, "5+3=8")
        ));

        assertTrue(result.needsReview());
        assertEquals(OcrEngineName.OPENAI, result.selectedEngineName());
        assertTrue(result.reason().contains("Only one"));
    }

    @Test
    void unanimousCanonicalAgreementCanProceedAutomatically() {
        OcrConsensusResult result = service.decide(List.of(
                OcrTestFixtures.success(OcrEngineName.OPENAI, "1. 2×3"),
                OcrTestFixtures.success(OcrEngineName.GEMINI, "2x3")
        ));

        assertFalse(result.needsReview());
        assertEquals(OcrEngineName.GEMINI, result.selectedEngineName());
        assertTrue(result.comparison().unanimousTextAgreement());
    }

    @Test
    void disagreementRequiresReview() {
        OcrConsensusResult result = service.decide(List.of(
                OcrTestFixtures.success(OcrEngineName.OPENAI, "5+3=8"),
                OcrTestFixtures.success(OcrEngineName.GEMINI, "5-3=2")
        ));

        assertTrue(result.needsReview());
        assertTrue(result.reason().contains("disagree"));
    }

    @Test
    void twoAgainstOneIsNotBlindMajorityConsensus() {
        OcrConsensusResult result = service.decide(List.of(
                OcrTestFixtures.success(OcrEngineName.OPENAI, "5+3=8"),
                OcrTestFixtures.success(OcrEngineName.GEMINI, "5+3=8"),
                OcrTestFixtures.success(OcrEngineName.QWEN, "5-3=2")
        ));

        assertTrue(result.needsReview());
        assertFalse(result.comparison().unanimousTextAgreement());
    }

    @Test
    void engineFailureKeepsReviewEvenWhenOtherEnginesAgree() {
        OcrConsensusResult result = service.decide(List.of(
                OcrTestFixtures.success(OcrEngineName.OPENAI, "5+3=8"),
                OcrTestFixtures.success(OcrEngineName.GEMINI, "5+3=8"),
                OcrTestFixtures.failure(OcrEngineName.MATHPIX)
        ));

        assertTrue(result.needsReview());
        assertTrue(result.reason().contains("failed"));
    }
}
