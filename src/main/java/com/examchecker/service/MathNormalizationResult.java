package com.examchecker.service;

import java.util.List;
import java.util.Objects;

public record MathNormalizationResult(
        String rawText,
        String canonicalText,
        String rulesVersion,
        List<MathNormalizationRule> appliedRules
) {

    public MathNormalizationResult {
        canonicalText = Objects.requireNonNull(canonicalText, "canonicalText must not be null");
        if (rulesVersion == null || rulesVersion.isBlank()) {
            throw new IllegalArgumentException("rulesVersion must not be blank");
        }
        appliedRules = List.copyOf(appliedRules);
    }
}
