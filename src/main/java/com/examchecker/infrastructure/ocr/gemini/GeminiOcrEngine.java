package com.examchecker.infrastructure.ocr.gemini;

import com.examchecker.infrastructure.ocr.core.OcrBundleParser;
import com.examchecker.infrastructure.ocr.core.OcrBundleResult;
import com.examchecker.infrastructure.ocr.core.OcrEngine;
import com.examchecker.infrastructure.ocr.core.OcrEngineName;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class GeminiOcrEngine implements OcrEngine {

    private final GeminiOcrService geminiOcrService;
    private final OcrBundleParser ocrBundleParser;

    public GeminiOcrEngine(
            GeminiOcrService geminiOcrService,
            OcrBundleParser ocrBundleParser
    ) {
        this.geminiOcrService = geminiOcrService;
        this.ocrBundleParser = ocrBundleParser;
    }

    @Override
    public OcrEngineName name() {
        return OcrEngineName.GEMINI;
    }

    @Override
    public OcrBundleResult extract(MultipartFile image) {

        String rawResponse = geminiOcrService.extractText(image);

        return ocrBundleParser.parse(rawResponse);
    }
}