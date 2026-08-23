package com.examchecker.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CanonicalMathNormalizer {

    public static final String RULES_VERSION = "canonical-math-v2";

    public String normalize(String text) {
        return normalizeWithTrace(text).canonicalText();
    }

    public MathNormalizationResult normalizeWithTrace(String text) {
        List<MathNormalizationRule> appliedRules = new ArrayList<>();

        if (text == null) {
            appliedRules.add(MathNormalizationRule.NULL_TO_EMPTY);
            return new MathNormalizationResult(null, "", RULES_VERSION, appliedRules);
        }

        String normalized = apply(
                text,
                text.trim(),
                MathNormalizationRule.TRIM_OUTER_WHITESPACE,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalizeUnicodeDigits(normalized),
                MathNormalizationRule.NORMALIZE_UNICODE_DIGITS,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized.replaceFirst(
                        "^(?:\\d+[.)]\\s+|\\(\\d+\\)\\.?\\s+|\\(?\\d+\\)?-\\s+)",
                        ""
                ),
                MathNormalizationRule.REMOVE_QUESTION_PREFIX,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized.replaceAll("\\s+", ""),
                MathNormalizationRule.REMOVE_WHITESPACE,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized.replace("＋", "+").replace("﹢", "+"),
                MathNormalizationRule.NORMALIZE_PLUS,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized
                        .replace("−", "-")
                        .replace("–", "-")
                        .replace("—", "-")
                        .replace("﹣", "-")
                        .replace("－", "-"),
                MathNormalizationRule.NORMALIZE_MINUS,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized.replace("×", "*").replace("x", "*").replace("X", "*"),
                MathNormalizationRule.NORMALIZE_MULTIPLICATION,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized
                        .replace("÷", "/")
                        .replace(":", "/")
                        .replace("⁄", "/")
                        .replace("∕", "/")
                        .replace("\\div", "/"),
                MathNormalizationRule.NORMALIZE_DIVISION,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized.replace("＝", "=").replace("﹦", "="),
                MathNormalizationRule.NORMALIZE_EQUALS,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized
                        .replace("（", "(")
                        .replace("﹙", "(")
                        .replace("）", ")")
                        .replace("﹚", ")"),
                MathNormalizationRule.NORMALIZE_PARENTHESES,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized.replaceAll("(?<=\\d),(?=\\d{1,2}(?:\\D|$))", "."),
                MathNormalizationRule.NORMALIZE_DECIMAL_COMMA,
                appliedRules
        );
        normalized = apply(
                normalized,
                normalized.replace("％", "%").replace("٪", "%"),
                MathNormalizationRule.NORMALIZE_PERCENT,
                appliedRules
        );
        return new MathNormalizationResult(text, normalized, RULES_VERSION, appliedRules);
    }

    private String normalizeUnicodeDigits(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            int numericValue = Character.digit(codePoint, 10);
            if (numericValue >= 0) {
                normalized.append((char) ('0' + numericValue));
            } else {
                normalized.appendCodePoint(codePoint);
            }
        });
        return normalized.toString();
    }

    private String apply(
            String before,
            String after,
            MathNormalizationRule rule,
            List<MathNormalizationRule> appliedRules
    ) {
        if (!before.equals(after)) {
            appliedRules.add(rule);
        }
        return after;
    }
}
