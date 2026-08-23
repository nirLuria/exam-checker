package com.examchecker.infrastructure.ocr.gemini;

import com.examchecker.infrastructure.ocr.contract.OcrEngine;
import com.examchecker.infrastructure.ocr.contract.OcrEngineMetadata;
import com.examchecker.infrastructure.ocr.contract.OcrEngineName;
import org.springframework.stereotype.Component;
import com.examchecker.infrastructure.ocr.preparation.QuestionPackage;

@Component
public class GeminiOcrEngine implements OcrEngine {

    private static final String ADAPTER_VERSION = "gemini-ocr-adapter-v1";

    private final GeminiOcrService geminiOcrService;
    private final GeminiProperties properties;

    public GeminiOcrEngine(
            GeminiOcrService geminiOcrService,
            GeminiProperties properties
    ) {
        this.geminiOcrService = geminiOcrService;
        this.properties = properties;
    }

    @Override
    public OcrEngineMetadata metadata() {
        return new OcrEngineMetadata(
                OcrEngineName.GEMINI,
                properties.model(),
                ADAPTER_VERSION
        );
    }

    @Override
    public String extractRaw(QuestionPackage questionPackage) {
        return geminiOcrService.extractText(
                questionPackage.image().content(),
                questionPackage.image().contentType()
        );
    }
}
