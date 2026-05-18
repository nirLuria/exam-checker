package com.examchecker.infrastructure.ocr.openai;

import com.examchecker.infrastructure.OcrService;
import com.examchecker.infrastructure.ocr.core.OcrBundleParser;
import com.examchecker.infrastructure.ocr.core.OcrBundleResult;
import com.examchecker.infrastructure.ocr.core.OcrEngine;
import com.examchecker.infrastructure.ocr.core.OcrEngineName;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class OpenAiOcrEngine implements OcrEngine {

    private final OcrService ocrService;
    private final OcrBundleParser ocrBundleParser;

    public OpenAiOcrEngine(
            OcrService ocrService,
            OcrBundleParser ocrBundleParser
    ) {
        this.ocrService = ocrService;
        this.ocrBundleParser = ocrBundleParser;
    }

    @Override
    public OcrEngineName name() {
        return OcrEngineName.OPENAI;
    }

    @Override
    public OcrBundleResult extract(MultipartFile file) {
        String json = ocrService.extractText(file);
        return ocrBundleParser.parse(json);
    }
}