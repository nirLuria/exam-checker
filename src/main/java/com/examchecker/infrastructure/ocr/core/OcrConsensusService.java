package com.examchecker.infrastructure.ocr.core;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class OcrConsensusService {

    public static final String POLICY_VERSION = "ocr-consensus-v1";

    private final OcrResultComparisonService comparisonService;

    public OcrConsensusService(OcrResultComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    public OcrConsensusResult decide(List<OcrEngineResult> engineResults) {
        List<OcrEngineResult> results = engineResults == null
                ? List.of()
                : List.copyOf(engineResults);
        OcrComparisonResult comparison = comparisonService.compare(results);

        List<OcrEngineResult> successfulResults = results.stream()
                .filter(OcrEngineResult::succeeded)
                .sorted(Comparator.comparing(result -> result.engineName().name()))
                .toList();

        if (successfulResults.isEmpty()) {
            String reason = results.isEmpty()
                    ? "No OCR engine results available"
                    : "All OCR engines failed";
            return result(null, results, comparison, true, reason);
        }

        OcrEngineResult selected = successfulResults.get(0);

        if (successfulResults.size() == 1) {
            return result(
                    selected,
                    results,
                    comparison,
                    true,
                    "Only one OCR engine succeeded; independent agreement is unavailable"
            );
        }

        if (!comparison.unanimousTextAgreement()
                || !comparison.unanimousOperatorAgreement()) {
            return result(
                    selected,
                    results,
                    comparison,
                    true,
                    "OCR engines disagree after canonical normalization"
            );
        }

        boolean anyEngineFailed = results.stream().anyMatch(OcrEngineResult::failed);
        if (anyEngineFailed) {
            return result(
                    selected,
                    results,
                    comparison,
                    true,
                    "Successful OCR engines agree, but at least one configured engine failed"
            );
        }

        return result(
                selected,
                results,
                comparison,
                false,
                "All successful OCR engines agree after canonical normalization"
        );
    }

    private OcrConsensusResult result(
            OcrEngineResult selected,
            List<OcrEngineResult> engineResults,
            OcrComparisonResult comparison,
            boolean needsReview,
            String reason
    ) {
        return new OcrConsensusResult(
                selected == null ? null : selected.engineName(),
                selected == null ? null : selected.bundle(),
                engineResults,
                comparison,
                needsReview,
                reason,
                POLICY_VERSION
        );
    }
}
