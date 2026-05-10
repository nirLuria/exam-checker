package com.examchecker.domain;

import org.springframework.stereotype.Component;

@Component
public class Grader {

    public boolean isCorrect(ParsedExpression parsedExpression) {
        return parsedExpression.getExpected() == parsedExpression.getStudentAnswer();
    }
}