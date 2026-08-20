package com.examchecker.infrastructure.ocr.core;

public enum OcrEngineFailureType {
    NONE,
    QUOTA_EXCEEDED,
    RATE_LIMIT,
    AUTH_ERROR,
    TIMEOUT,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    PARSE_ERROR,
    UNKNOWN
}