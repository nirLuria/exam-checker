package com.examchecker.question;

import java.util.List;
import java.util.Objects;

public record OcrContext(
        String promptText,
        QuestionType questionType,
        List<String> units
) {
    public OcrContext {
        promptText = promptText == null ? "" : promptText.trim();
        Objects.requireNonNull(questionType, "questionType must not be null");
        units = units == null
                ? List.of()
                : units.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(unit -> !unit.isEmpty())
                .distinct()
                .toList();
    }
}
