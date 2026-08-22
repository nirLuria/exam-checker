package com.examchecker.application;

import com.examchecker.image.ImageQualityReport;
import com.examchecker.image.ImageQualityDecision;
import com.examchecker.image.ImageQualityService;
import com.examchecker.image.RejectedQuestionImageArchive;
import com.examchecker.infrastructure.OpenAiClient;
import com.examchecker.infrastructure.ocr.core.MultiEngineOcrService;
import com.examchecker.infrastructure.ocr.core.OcrBundleResult;
import com.examchecker.infrastructure.ocr.core.OcrConsensusResult;
import com.examchecker.infrastructure.ocr.core.OcrConsensusService;
import com.examchecker.infrastructure.ocr.core.OcrEngineResult;
import com.examchecker.infrastructure.ocr.core.OcrEngineName;
import com.examchecker.infrastructure.ocr.core.SuspiciousCheckResult;
import com.examchecker.service.MathTextNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CheckService {

    private final MultiEngineOcrService multiEngineOcrService;
    private final OpenAiClient openAiClient;
    private final MathTextNormalizer mathTextNormalizer;
    private final ImageQualityService imageQualityService;
    private final RejectedQuestionImageArchive rejectedQuestionImageArchive;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OcrConsensusService ocrConsensusService;

    public CheckService(
            MultiEngineOcrService multiEngineOcrService,
            OpenAiClient openAiClient,
            MathTextNormalizer mathTextNormalizer,
            ImageQualityService imageQualityService,
            RejectedQuestionImageArchive rejectedQuestionImageArchive,
            OcrConsensusService ocrConsensusService
    ) {
        this.multiEngineOcrService = multiEngineOcrService;
        this.openAiClient = openAiClient;
        this.mathTextNormalizer = mathTextNormalizer;
        this.imageQualityService = imageQualityService;
        this.rejectedQuestionImageArchive = rejectedQuestionImageArchive;
        this.ocrConsensusService = ocrConsensusService;
    }

    public Map<String, Object> check(MultipartFile file) {
        try {
            ImageQualityReport imageQualityReport = imageQualityService.analyze(file);
            RejectedQuestionImageArchive.ArchiveResult archiveResult =
                    rejectedQuestionImageArchive.archive(file, imageQualityReport);

            if (imageQualityReport.decision() == ImageQualityDecision.RETRY_CAPTURE) {
                return Map.ofEntries(
                        Map.entry("processingStatus", ImageQualityDecision.RETRY_CAPTURE.name()),
                        Map.entry("ocrSkipped", true),
                        Map.entry("needsTeacherReview", false),
                        Map.entry("reason", imageQualityReport.reason()),
                        Map.entry("imageQualityReport", imageQualityReport),
                        Map.entry("rejectedImageArchive", archiveResult)
                );
            }

            List<OcrEngineResult> engineResults = new ArrayList<>();

            OcrEngineResult openAiResult =
                    multiEngineOcrService.extractWithEngine(
                            OcrEngineName.OPENAI,
                            file
                    );

            engineResults.add(openAiResult);

            OcrConsensusResult consensus = ocrConsensusService.decide(engineResults);
            boolean geminiAlreadyRun = false;

            if (consensus.selectedBundle() == null) {
                OcrEngineResult geminiResult =
                        multiEngineOcrService.extractWithEngine(
                                OcrEngineName.GEMINI,
                                file
                        );
                engineResults.add(geminiResult);
                geminiAlreadyRun = true;
                consensus = ocrConsensusService.decide(engineResults);

                if (consensus.selectedBundle() == null) {
                    throw new RuntimeException("All OCR engines failed: " + consensus.reason());
                }
            }

            OcrBundleResult ocrBundle = consensus.selectedBundle();

            BundleState state = buildBundleState(ocrBundle);
            String selectedText = majorityText(state);
            Map<String, Object> analysis = analyzeExerciseSafely(selectedText);

            boolean correct = Boolean.TRUE.equals(analysis.get("correct"));

            boolean openAiMathParsed = isMathParsed(analysis);
            boolean openAiMathWrong = openAiMathParsed && !correct;

            boolean riskyOperator =
                    hasRiskyOperator(state.primaryOperators())
                            || hasRiskyOperator(state.verificationOperators());

            boolean confidenceGateTriggered =
                    !correct && riskyOperator;

            boolean preliminaryNeedsReview =
                    calculateNeedsReview(
                            imageQualityReport,
                            consensus,
                            engineResults,
                            state,
                            confidenceGateTriggered
                    );

            boolean shouldRunGemini =
                    !geminiAlreadyRun
                            && (preliminaryNeedsReview
                            || confidenceGateTriggered
                            || openAiMathWrong);

            if (shouldRunGemini)
            {
                OcrEngineResult geminiResult =
                        multiEngineOcrService.extractWithEngine(
                                OcrEngineName.GEMINI,
                                file
                        );

                engineResults.add(geminiResult);

                consensus = ocrConsensusService.decide(engineResults);

                if (consensus.selectedBundle() == null) {
                    throw new RuntimeException("OCR consensus failed: " + consensus.reason());
                }

                ocrBundle = consensus.selectedBundle();
                state = buildBundleState(ocrBundle);
                selectedText = majorityText(state);
                analysis = analyzeExerciseSafely(selectedText);

                correct = Boolean.TRUE.equals(analysis.get("correct"));

                riskyOperator =
                        hasRiskyOperator(state.primaryOperators())
                                || hasRiskyOperator(state.verificationOperators());

                confidenceGateTriggered =
                        !correct && riskyOperator;
            }

            boolean anyEngineFailed = anyEngineFailed(engineResults);
            boolean engineDisagreement = engineDisagreement(engineResults);

            boolean finalNeedsReview =
                    openAiMathWrong
                            || calculateNeedsReview(
                            imageQualityReport,
                            consensus,
                            engineResults,
                            state,
                            confidenceGateTriggered
                    );

            String ocrEngineSummary = buildOcrEngineSummary(engineResults);

            return Map.ofEntries(
                    Map.entry("rawText", selectedText),
                    Map.entry("verificationText", state.verificationText()),
                    Map.entry("thresholdText", state.thresholdText()),

                    Map.entry("normalizedRawText", state.normalizedPrimaryText()),
                    Map.entry("normalizedVerificationText", state.normalizedVerificationText()),
                    Map.entry("normalizedThresholdText", state.normalizedThresholdText()),

                    Map.entry("isClearlyReadable", state.primaryReadable() && state.verificationReadable()),
                    Map.entry("needsTeacherReview", finalNeedsReview),
                    Map.entry("suspicious", finalNeedsReview || state.suspiciousOriginal()),
                    Map.entry("suspiciousFlatExpression", state.suspiciousFlatExpression()),

                    Map.entry("ocrEngineSummary", ocrEngineSummary),
                    Map.entry("ocrConsensusReason", safe(consensus.reason())),
                    Map.entry("ocrConsensusPolicyVersion", consensus.policyVersion()),
                    Map.entry("selectedOcrEngine", consensus.selectedEngineName()),
                    Map.entry("ocrComparison", consensus.comparison()),

                    Map.entry("suspiciousReason",
                            finalNeedsReview
                                    ? buildSuspiciousReason(
                                    imageQualityReport,
                                    state,
                                    confidenceGateTriggered,
                                    openAiMathWrong,
                                    anyEngineFailed,
                                    engineDisagreement
                            )
                                    : ""
                    ),

                    Map.entry("suggestedRawText", safe(state.suspiciousCheck().suggestedRawText())),

                    Map.entry("expression", safe(analysis.get("expression"))),
                    Map.entry("expected", safe(analysis.get("expected"))),
                    Map.entry("studentAnswer", safe(analysis.get("studentAnswer"))),
                    Map.entry("correct", safe(analysis.get("correct"))),

                    Map.entry("primaryOperators", state.primaryOperators()),
                    Map.entry("verificationOperators", state.verificationOperators()),
                    Map.entry("thresholdOperators", state.thresholdOperators()),
                    Map.entry("sameOperators", state.sameOperators()),
                    Map.entry("thresholdOperatorsMatch", state.thresholdOperatorsMatch()),
                    Map.entry("riskyOperator", riskyOperator),

                    Map.entry("openAiMathWrong", openAiMathWrong),

                    Map.entry("confidenceGateReason",
                            confidenceGateTriggered
                                    ? "Exercise is mathematically incorrect and contains a risky operator"
                                    : ""),

                    Map.entry("imageQualityReport", imageQualityReport),
                    Map.entry("rejectedImageArchive", archiveResult),
                    Map.entry("ocrSkipped", false),
                    Map.entry("processingStatus", "COMPLETED")
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to check exercise", e);
        }
    }

    private String majorityText(BundleState state) {
        String p = state.normalizedPrimaryText();
        String v = state.normalizedVerificationText();
        String t = state.normalizedThresholdText();

        if (p.equals(v)) return state.primaryText();
        if (p.equals(t)) return state.primaryText();
        if (v.equals(t)) return state.verificationText();

        return state.primaryText();
    }

    private BundleState buildBundleState(OcrBundleResult ocrBundle) {
        String primaryText = ocrBundle.primary().rawText();
        String verificationText = ocrBundle.verification().rawText();
        String thresholdText = ocrBundle.thresholdRead().rawText();

        String normalizedPrimaryText = mathTextNormalizer.normalize(primaryText);
        String normalizedVerificationText = mathTextNormalizer.normalize(verificationText);
        String normalizedThresholdText = mathTextNormalizer.normalize(thresholdText);

        String primaryOperators = extractOperators(normalizedPrimaryText);
        String verificationOperators = extractOperators(normalizedVerificationText);
        String thresholdOperators = extractOperators(normalizedThresholdText);

        boolean primaryReadable = ocrBundle.primary().clearlyReadable();
        boolean verificationReadable = ocrBundle.verification().clearlyReadable();

        boolean sameText = normalizedPrimaryText.equals(normalizedVerificationText);
        boolean sameOperators = primaryOperators.equals(verificationOperators);
        boolean thresholdOperatorsMatch = primaryOperators.equals(thresholdOperators);
        boolean suspiciousFlatExpression = looksLikeCollapsedExpression(normalizedPrimaryText);

        SuspiciousCheckResult suspiciousCheck = ocrBundle.suspiciousCheck();
        boolean suspiciousOriginal =
                suspiciousCheck != null && suspiciousCheck.suspicious();

        return new BundleState(
                primaryText,
                verificationText,
                thresholdText,
                normalizedPrimaryText,
                normalizedVerificationText,
                normalizedThresholdText,
                primaryOperators,
                verificationOperators,
                thresholdOperators,
                primaryReadable,
                verificationReadable,
                sameText,
                sameOperators,
                thresholdOperatorsMatch,
                suspiciousFlatExpression,
                suspiciousOriginal,
                suspiciousCheck
        );
    }

    private Map<String, Object> analyzeExerciseSafely(String primaryText) {
        String analysisJson = cleanJson(openAiClient.analyzeExercise(primaryText));

        try {
            return objectMapper.readValue(analysisJson, Map.class);
        } catch (Exception parseError) {
            return Map.of(
                    "expression", "",
                    "expected", "",
                    "studentAnswer", "",
                    "correct", false
            );
        }
    }

    private boolean calculateNeedsReview(
            ImageQualityReport imageQualityReport,
            OcrConsensusResult consensus,
            List<OcrEngineResult> engineResults,
            BundleState state,
            boolean confidenceGateTriggered
    ) {


        boolean strongOcrAgreement =
                state.sameText()
                        && state.sameOperators()
                        && state.thresholdOperatorsMatch()
                        && !engineDisagreement(engineResults)
                        && !consensus.needsReview();

        boolean severeImageQualityIssue =
                imageQualityReport.blurry()
                        || (
                        imageQualityReport.tooSmall()
                                && imageQualityReport.lowContrast()
                                && imageQualityReport.contrastScore() < 15
                                && !strongOcrAgreement);

        boolean bothUnreadable =
                !state.primaryReadable() && !state.verificationReadable();

        return consensus.needsReview()
                || imageQualityReport.decision() == ImageQualityDecision.TEACHER_REVIEW
                || anyEngineFailed(engineResults)
                || engineDisagreement(engineResults)
                || severeImageQualityIssue
                || bothUnreadable
                || !state.sameOperators()
                || !state.thresholdOperatorsMatch()
                || state.suspiciousFlatExpression()
                || confidenceGateTriggered;
    }

    private boolean anyEngineFailed(List<OcrEngineResult> engineResults) {
        return engineResults.stream()
                .anyMatch(OcrEngineResult::failed);
    }

    private boolean engineDisagreement(List<OcrEngineResult> engineResults) {
        return engineResults.stream()
                .filter(OcrEngineResult::succeeded)
                .map(result -> mathTextNormalizer.normalize(result.bundle().primary().rawText()))
                .distinct()
                .count() > 1;
    }

    private String buildOcrEngineSummary(List<OcrEngineResult> engineResults) {
        if (engineResults == null || engineResults.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();

        for (OcrEngineResult result : engineResults) {
            summary.append(result.engineName())
                    .append(": ");

            if (result.failed()) {
                summary.append("FAILED - ")
                        .append(safe(result.failureType()));
            } else {
                summary.append(safe(result.bundle().primary().rawText()));
            }

            summary.append(" | ");
        }

        return summary.toString().trim();
    }

    private String buildSuspiciousReason(
            ImageQualityReport imageQualityReport,
            BundleState state,
            boolean confidenceGateTriggered,
            boolean openAiMathWrong,
            boolean anyEngineFailed,
            boolean engineDisagreement
    ) {
        StringBuilder reason = new StringBuilder();

        boolean severeImageQualityIssue =
                imageQualityReport.blurry()
                        || (
                        imageQualityReport.tooSmall()
                                && imageQualityReport.lowContrast()
                                && imageQualityReport.contrastScore() < 15);

        if (severeImageQualityIssue) {
            reason.append("Image quality issue: ")
                    .append(imageQualityReport.reason())
                    .append(". ");
        }

        if (anyEngineFailed) {
            reason.append("One or more OCR engines failed. ");
        }

        if (engineDisagreement) {
            reason.append("OCR engines produced different text. ");
        }

        if (!state.primaryReadable() && !state.verificationReadable()) {
            reason.append("Both primary and verification OCR say image is not clearly readable. ");
        }

        if (!state.sameText()) {
            reason.append("Primary and verification OCR produced different text. ");
        }

        if (!state.sameOperators()) {
            reason.append("Operator mismatch between primary and verification OCR. ");
        }

        if (!state.thresholdOperatorsMatch()) {
            reason.append("Operator mismatch between original/sharpened and threshold OCR. ");
        }

        if (state.suspiciousFlatExpression()) {
            reason.append("Expression looks collapsed: equals sign exists, but left side is only a long number with no operator. ");
        }

        if (confidenceGateTriggered) {
            reason.append("Exercise is mathematically incorrect and contains a risky operator. ");
        }

        if (openAiMathWrong) {
            reason.append("OpenAI OCR produced a mathematically incorrect exercise; second engine was required. ");
        }

        if (state.suspiciousCheck() != null && state.suspiciousCheck().suspicious()) {
            reason.append("Suspicious OCR check: ")
                    .append(safe(state.suspiciousCheck().reason()))
                    .append(". ");
        }

        return reason.toString().trim();
    }

    private boolean isMathParsed(Map<String, Object> analysis) {
        if (analysis == null) {
            return false;
        }

        return hasText(analysis.get("expression"))
                && hasText(analysis.get("expected"))
                && hasText(analysis.get("studentAnswer"));
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().trim().isEmpty();
    }

    private String cleanJson(String json) {
        return json
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    private Object safe(Object value) {
        return value == null ? "" : value;
    }

    private String extractOperators(String text) {
        if (text == null) {
            return "";
        }

        return text.replaceAll("[0-9\\s=()]", "");
    }

    private boolean hasRiskyOperator(String operators) {
        if (operators == null) {
            return false;
        }

        return operators.contains("^")
                || operators.contains("*")
                || operators.contains("×")
                || operators.contains("x")
                || operators.contains("-")
                || operators.contains("²");
    }

    private boolean looksLikeCollapsedExpression(String text) {
        if (text == null) {
            return false;
        }

        String compact = text.replaceAll("\\s+", "");

        return compact.matches("\\d{3,}=\\d+");
    }

    private record BundleState(
            String primaryText,
            String verificationText,
            String thresholdText,
            String normalizedPrimaryText,
            String normalizedVerificationText,
            String normalizedThresholdText,
            String primaryOperators,
            String verificationOperators,
            String thresholdOperators,
            boolean primaryReadable,
            boolean verificationReadable,
            boolean sameText,
            boolean sameOperators,
            boolean thresholdOperatorsMatch,
            boolean suspiciousFlatExpression,
            boolean suspiciousOriginal,
            SuspiciousCheckResult suspiciousCheck
    ) {
    }
}
