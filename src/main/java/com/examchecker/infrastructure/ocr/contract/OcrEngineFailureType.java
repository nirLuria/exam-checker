package com.examchecker.infrastructure.ocr.contract;

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
