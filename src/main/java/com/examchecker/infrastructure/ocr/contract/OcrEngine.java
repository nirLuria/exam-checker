package com.examchecker.infrastructure.ocr.contract;

import com.examchecker.infrastructure.ocr.preparation.QuestionPackage;

public interface OcrEngine {

    OcrEngineMetadata metadata();

    String extractRaw(QuestionPackage questionPackage);
}
