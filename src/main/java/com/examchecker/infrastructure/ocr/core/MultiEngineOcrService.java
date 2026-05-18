package com.examchecker.infrastructure.ocr.core;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class MultiEngineOcrService {

    private final List<OcrEngine> engines;

    public MultiEngineOcrService(List<OcrEngine> engines) {
        this.engines = engines;
    }

    public List<OcrEngineBundleResult> extractWithAllEngines(MultipartFile file) {
        List<OcrEngineBundleResult> results = new ArrayList<>();

        for (OcrEngine engine : engines) {
            try {
                results.add(new OcrEngineBundleResult(
                        engine.name(),
                        engine.extract(file),
                        false,
                        ""
                ));
            } catch (Exception e) {
                results.add(new OcrEngineBundleResult(
                        engine.name(),
                        emptyBundle(),
                        true,
                        e.getMessage()
                ));
            }
        }

        return results;
    }

    private OcrBundleResult emptyBundle() {
        OcrReading emptyReading = new OcrReading("", false);
        SuspiciousCheckResult emptySuspicious =
                new SuspiciousCheckResult(false, "", "");

        return new OcrBundleResult(
                emptyReading,
                emptyReading,
                emptyReading,
                emptySuspicious
        );
    }
}