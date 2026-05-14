package com.examchecker.infrastructure.ocr;

import com.examchecker.infrastructure.OcrService;
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