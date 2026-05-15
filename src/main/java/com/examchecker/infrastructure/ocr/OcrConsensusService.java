package com.examchecker.infrastructure.ocr;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OcrConsensusService {

    public OcrConsensusResult decide(List<OcrEngineBundleResult> engineResults) {
        if (engineResults == null || engineResults.isEmpty()) {
            return new OcrConsensusResult(
                    null,
                    List.of(),
                    true,
                    "No OCR engine results available"
            );
        }

        OcrEngineBundleResult firstSuccessful = engineResults.stream()
                .filter(result -> !result.failed())
                .findFirst()
                .orElse(null);

        if (firstSuccessful == null) {
            return new OcrConsensusResult(
                    null,
                    engineResults,
                    true,
                    "All OCR engines failed"
            );
        }

        return new OcrConsensusResult(
                firstSuccessful.bundle(),
                engineResults,
                false,
                ""
        );
    }
}