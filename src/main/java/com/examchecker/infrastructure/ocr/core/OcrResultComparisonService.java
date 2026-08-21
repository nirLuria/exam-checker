package com.examchecker.infrastructure.ocr.core;

import com.examchecker.service.CanonicalMathNormalizer;
import com.examchecker.service.MathNormalizationResult;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class OcrResultComparisonService {

    private final CanonicalMathNormalizer normalizer;

    public OcrResultComparisonService(CanonicalMathNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public OcrComparisonResult compare(List<OcrEngineResult> engineResults) {
        List<OcrEngineTextEvidence> evidence = safeResults(engineResults).stream()
                .filter(OcrEngineResult::succeeded)
                .sorted(Comparator.comparing(result -> result.engineName().name()))
                .map(this::toEvidence)
                .toList();

        List<String> distinctTexts = evidence.stream()
                .map(item -> item.normalization().canonicalText())
                .distinct()
                .sorted()
                .toList();

        long distinctOperators = evidence.stream()
                .map(OcrEngineTextEvidence::operatorSignature)
                .distinct()
                .count();

        boolean enoughEvidence = evidence.size() >= 2;

        return new OcrComparisonResult(
                evidence.size(),
                enoughEvidence && distinctTexts.size() == 1,
                enoughEvidence && distinctOperators == 1,
                distinctTexts,
                evidence
        );
    }

    private OcrEngineTextEvidence toEvidence(OcrEngineResult result) {
        String rawText = result.bundle().primary().rawText();
        MathNormalizationResult normalization = normalizer.normalizeWithTrace(rawText);

        return new OcrEngineTextEvidence(
                result.engineName(),
                rawText,
                normalization,
                extractOperators(normalization.canonicalText())
        );
    }

    private String extractOperators(String canonicalText) {
        return canonicalText.replaceAll("[0-9\\s=().]", "");
    }

    private List<OcrEngineResult> safeResults(List<OcrEngineResult> engineResults) {
        return engineResults == null ? List.of() : List.copyOf(engineResults);
    }
}
