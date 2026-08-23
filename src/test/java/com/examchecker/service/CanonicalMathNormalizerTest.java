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
        assertEquals("canonical-math-v2", result.rulesVersion());
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
    void doesNotMistakeLeadingDecimalForQuestionPrefix() {
        assertEquals("12.5+0.25=12.75", normalizer.normalize("12.5+0.25=12.75"));
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

    @ParameterizedTest
    @ValueSource(strings = {"−", "–", "—", "﹣", "－"})
    void normalizesUnicodeMinusWithoutChangingOperands(String minusSign) {
        assertEquals("5-3=2", normalizer.normalize("5" + minusSign + "3=2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"＋", "﹢"})
    void normalizesUnicodePlus(String plusSign) {
        assertEquals("2+3=5", normalizer.normalize("2" + plusSign + "3=5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"＝", "﹦"})
    void normalizesUnicodeEquals(String equalsSign) {
        assertEquals("2+3=5", normalizer.normalize("2+3" + equalsSign + "5"));
    }

    @Test
    void normalizesUnicodeDigitsAndParentheses() {
        assertEquals("(12+3)=15", normalizer.normalize("（１２+٣）＝١٥"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"⁄", "∕"})
    void normalizesUnicodeFractionSlash(String slash) {
        assertEquals("1/2", normalizer.normalize("1" + slash + "2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"％", "٪"})
    void normalizesUnicodePercent(String percentSign) {
        assertEquals("50%", normalizer.normalize("50" + percentSign));
    }

    @Test
    void normalizesUnambiguousElementaryDecimalComma() {
        assertEquals("12.5+0.25=12.75", normalizer.normalize("12,5 + 0,25 = 12,75"));
    }

    @Test
    void preservesThreeDigitCommaBecauseItMayBeThousandsSeparator() {
        assertEquals("1,000+2", normalizer.normalize("1,000 + 2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5−3＝2",
            "（１２＋٣）＝١٥",
            "50％",
            "12,5+0,25=12,75",
            "1⁄2"
    })
    void normalizationIsIdempotent(String input) {
        String once = normalizer.normalize(input);
        assertEquals(once, normalizer.normalize(once));
    }
}
