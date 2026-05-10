package com.examchecker.domain;

public class ParsedExpression {

    private final String expression;
    private final int expected;
    private final int studentAnswer;

    public ParsedExpression(String expression, int expected, int studentAnswer) {
        this.expression = expression;
        this.expected = expected;
        this.studentAnswer = studentAnswer;
    }

    public String getExpression() {
        return expression;
    }

    public int getExpected() {
        return expected;
    }

    public int getStudentAnswer() {
        return studentAnswer;
    }
}