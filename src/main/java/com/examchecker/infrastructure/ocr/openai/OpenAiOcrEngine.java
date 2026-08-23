package com.examchecker.infrastructure.ocr.openai;

import com.examchecker.infrastructure.OcrService;
import com.examchecker.infrastructure.ocr.contract.OcrEngine;
import com.examchecker.infrastructure.ocr.contract.OcrEngineMetadata;
import com.examchecker.infrastructure.ocr.contract.OcrEngineName;
import org.springframework.stereotype.Component;
import com.examchecker.infrastructure.ocr.preparation.QuestionPackage;

@Component
public class OpenAiOcrEngine implements OcrEngine {

    private static final String ADAPTER_VERSION = "openai-ocr-adapter-v1";

    private final OcrService ocrService;

    public OpenAiOcrEngine(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public OcrEngineMetadata metadata() {
        return new OcrEngineMetadata(
                OcrEngineName.OPENAI,
                ocrService.modelVersion(),
                ADAPTER_VERSION
        );
    }

    @Override
    public String extractRaw(QuestionPackage questionPackage) {
        return ocrService.extractText(
                questionPackage.image().content(),
                questionPackage.image().contentType()
        );
    }
}
