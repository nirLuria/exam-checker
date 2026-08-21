package com.examchecker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalMathNormalizerTest {

    private final CanonicalMathNormalizer normalizer = new CanonicalMathNormalizer();

    @Test
    void returnsEmptyStringForNull() {
        assertEquals("", normalizer.normalize(null));
    }

    @Test
    void removesWhitespace() {
        assertEquals("12+34=46", normalizer.normalize(" 12 \t+\n 34 = 46 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"×", "x", "X"})
    void normalizesMultiplicationSigns(String multiplicationSign) {
        assertEquals("2*3", normalizer.normalize("2" + multiplicationSign + "3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"÷", ":", "\\div"})
    void normalizesSupportedDivisionSigns(String divisionSign) {
        assertEquals("6/2", normalizer.normalize("6" + divisionSign + "2"));
    }

    @ParameterizedTest
    @CsvSource({
            "'1. 2+3', '2+3'",
            "'2) 2+3', '2+3'",
            "'(3). 2+3', '2+3'",
            "'(4)- 2+3', '2+3'"
    })
    void removesQuestionNumberAtStart(String input, String expected) {
        assertEquals(expected, normalizer.normalize(input));
    }

    @Test
    void recordsAppliedRulesInExecutionOrder() {
        MathNormalizationResult result = normalizer.normalizeWithTrace(" 1. 2 x 3 ÷ 6 ");

        assertEquals("2*3/6", result.canonicalText());
        assertEquals("canonical-math-v1", result.rulesVersion());
        assertEquals(List.of(
                MathNormalizationRule.TRIM_OUTER_WHITESPACE,
                MathNormalizationRule.REMOVE_QUESTION_PREFIX,
                MathNormalizationRule.REMOVE_WHITESPACE,
                MathNormalizationRule.NORMALIZE_MULTIPLICATION,
                MathNormalizationRule.NORMALIZE_DIVISION
        ), result.appliedRules());
    }

    @Test
    void doesNotMistakeSubtractionForQuestionPrefix() {
        assertEquals("5-3=2", normalizer.normalize("5-3=2"));
    }

    @Test
    void recordsNoRulesWhenTextIsAlreadyCanonical() {
        MathNormalizationResult result = normalizer.normalizeWithTrace("2+3=5");

        assertEquals(List.of(), result.appliedRules());
    }

    @Test
    void recordsNullConversionExplicitly() {
        MathNormalizationResult result = normalizer.normalizeWithTrace(null);

        assertEquals("", result.canonicalText());
        assertEquals(List.of(MathNormalizationRule.NULL_TO_EMPTY), result.appliedRules());
    }
}
