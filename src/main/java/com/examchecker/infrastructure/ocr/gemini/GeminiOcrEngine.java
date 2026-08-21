package com.examchecker.infrastructure.ocr.gemini;

import com.examchecker.infrastructure.ocr.core.OcrEngine;
import com.examchecker.infrastructure.ocr.core.OcrEngineMetadata;
import com.examchecker.infrastructure.ocr.core.OcrEngineName;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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
    public String extractRaw(MultipartFile image) {
        return geminiOcrService.extractText(image);
    }
}
