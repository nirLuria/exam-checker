package com.examchecker.domain;

import org.springframework.stereotype.Component;

@Component
public class ExpressionParser {

    public ParsedExpression parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("OCR text is null");
        }

        String cleaned = text
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .trim();

        String[] parts = cleaned.split("=");

        if (parts.length != 2) {
            throw new IllegalArgumentException("OCR text is not in expected format: " + cleaned);
        }

        String left = parts[0];
        String right = parts[1];

        if (right.isBlank()) {
            throw new IllegalArgumentException("Student answer is empty in OCR text: " + cleaned);
        }

        int studentAnswer = Integer.parseInt(right);

        String[] numbers = left.split("\\+");

        if (numbers.length != 2) {
            throw new IllegalArgumentException("Expression is not a simple addition: " + left);
        }

        int a = Integer.parseInt(numbers[0]);
        int b = Integer.parseInt(numbers[1]);

        int expected = a + b;

        return new ParsedExpression(left, expected, studentAnswer);
    }
}