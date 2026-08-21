package com.examchecker.infrastructure.ocr.core;

import com.examchecker.service.CanonicalMathNormalizer;
import com.examchecker.service.MathNormalizationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrResultComparisonServiceTest {

    private final OcrResultComparisonService service =
            new OcrResultComparisonService(new CanonicalMathNormalizer());

    @Test
    void recognizesAgreementAfterCanonicalNormalizationAndKeepsTrace() {
        OcrComparisonResult comparison = service.compare(List.of(
                OcrTestFixtures.success(OcrEngineName.OPENAI, "1. 2 × 3"),
                OcrTestFixtures.success(OcrEngineName.GEMINI, "2x3")
        ));

        assertTrue(comparison.unanimousTextAgreement());
        assertTrue(comparison.unanimousOperatorAgreement());
        assertEquals(List.of("2*3"), comparison.distinctCanonicalTexts());
        assertEquals(List.of(OcrEngineName.GEMINI, OcrEngineName.OPENAI),
                comparison.evidence().stream().map(OcrEngineTextEvidence::engineName).toList());
        assertTrue(comparison.evidence().get(0).normalization().appliedRules()
                .contains(MathNormalizationRule.NORMALIZE_MULTIPLICATION));
    }

    @Test
    void exposesTextAndOperatorDisagreement() {
        OcrComparisonResult comparison = service.compare(List.of(
                OcrTestFixtures.success(OcrEngineName.GEMINI, "5+3=8"),
                OcrTestFixtures.success(OcrEngineName.OPENAI, "5-3=2")
        ));

        assertFalse(comparison.unanimousTextAgreement());
        assertFalse(comparison.unanimousOperatorAgreement());
        assertEquals(List.of("5+3=8", "5-3=2"), comparison.distinctCanonicalTexts());
    }

    @Test
    void excludesFailedEnginesFromTextComparison() {
        OcrComparisonResult comparison = service.compare(List.of(
                OcrTestFixtures.failure(OcrEngineName.GEMINI),
                OcrTestFixtures.success(OcrEngineName.OPENAI, "5+3=8")
        ));

        assertEquals(1, comparison.successfulEngineCount());
        assertFalse(comparison.unanimousTextAgreement());
    }
}
