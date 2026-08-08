package com.examchecker.infrastructure.ocr.googlevision;

import com.examchecker.infrastructure.ocr.core.OcrBundleParser;
import com.examchecker.infrastructure.ocr.core.OcrBundleResult;
import com.examchecker.infrastructure.ocr.core.OcrEngine;
import com.examchecker.infrastructure.ocr.core.OcrEngineName;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class GoogleVisionOcrEngine implements OcrEngine {

    private final GoogleVisionOcrService googleVisionOcrService;
    private final OcrBundleParser ocrBundleParser;

    public GoogleVisionOcrEngine(
            GoogleVisionOcrService googleVisionOcrService,
            OcrBundleParser ocrBundleParser
    ) {
        this.googleVisionOcrService = googleVisionOcrService;
        this.ocrBundleParser = ocrBundleParser;
    }

    @Override
    public OcrBundleResult extract(MultipartFile image) {

        String json =
                googleVisionOcrService.extractText(image);

        return ocrBundleParser.parse(json);
    }

    @Override
    public OcrEngineName name() {
        return OcrEngineName.GOOGLE_VISION;
    }
}