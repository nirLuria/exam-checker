package com.examchecker.infrastructure.ocr.core;

public class OcrBundleParseException extends RuntimeException {

    public OcrBundleParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public OcrBundleParseException(String message) {
        super(message);
    }
}
