package com.examchecker.question;

public record QuestionReference(
        String examId,
        int pageNumber,
        String questionNumber,
        String subQuestionNumber
) {
    public QuestionReference {
        examId = requireText(examId, "examId");
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        questionNumber = requireText(questionNumber, "questionNumber");
        subQuestionNumber = subQuestionNumber == null ? "" : subQuestionNumber.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
