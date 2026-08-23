package com.examchecker.infrastructure.ocr.core;

import com.examchecker.question.QuestionPackage;

public interface OcrEngine {

    OcrEngineMetadata metadata();

    String extractRaw(QuestionPackage questionPackage);
}
