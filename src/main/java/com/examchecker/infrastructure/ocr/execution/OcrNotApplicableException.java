package com.examchecker.infrastructure.ocr.execution;

public class OcrNotApplicableException extends RuntimeException {

    public OcrNotApplicableException(String message) {
        super(message);
    }
}
