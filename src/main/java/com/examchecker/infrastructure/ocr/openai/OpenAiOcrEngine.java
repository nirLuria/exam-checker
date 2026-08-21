package com.examchecker.infrastructure.ocr.openai;

import com.examchecker.infrastructure.OcrService;
import com.examchecker.infrastructure.ocr.core.OcrEngine;
import com.examchecker.infrastructure.ocr.core.OcrEngineMetadata;
import com.examchecker.infrastructure.ocr.core.OcrEngineName;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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
    public String extractRaw(MultipartFile file) {
        return ocrService.extractText(file);
    }
}
