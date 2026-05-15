package com.examchecker.application;

import com.examchecker.image.ImageQualityReport;
import com.examchecker.image.ImageQualityService;
import com.examchecker.infrastructure.OcrService;
import com.examchecker.infrastructure.OpenAiClient;
import com.examchecker.service.MathTextNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.examchecker.infrastructure.ocr.OcrBundleParser;
import com.examchecker.infrastructure.ocr.OcrBundleResult;
import com.examchecker.infrastructure.ocr.SuspiciousCheckResult;
import java.util.Map;
import com.examchecker.infrastructure.ocr.MultiEngineOcrService;
import com.examchecker.infrastructure.ocr.OcrEngineBundleResult;
import java.util.List;

@Service
public class CheckService {

    private final MultiEngineOcrService multiEngineOcrService;
    private final OpenAiClient openAiClient;
    private final MathTextNormalizer mathTextNormalizer;
    private final ImageQualityService imageQualityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OcrBundleParser ocrBundleParser;


    public CheckService(
            MultiEngineOcrService multiEngineOcrService,
            OpenAiClient openAiClient,
            MathTextNormalizer mathTextNormalizer,
            ImageQualityService imageQualityService,
            OcrBundleParser ocrBundleParser
    ) {
        this.multiEngineOcrService = multiEngineOcrService;
        this.openAiClient = openAiClient;
        this.mathTextNormalizer = mathTextNormalizer;
        this.imageQualityService = imageQualityService;
        this.ocrBundleParser = ocrBundleParser;
    }

    public Map<String, Object> check(MultipartFile file) {
        try {
            ImageQualityReport imageQualityReport = imageQualityService.analyze(file);

            List<OcrEngineBundleResult> engineResults =
                    multiEngineOcrService.extractWithAllEngines(file);

            OcrEngineBundleResult primaryEngineResult = engineResults.get(0);

            if (primaryEngineResult.failed()) {
                throw new RuntimeException(
                        "Primary OCR engine failed: " + primaryEngineResult.failureReason()
                );
            }


            OcrBundleResult ocrBundle = primaryEngineResult.bundle();

            String primaryText = ocrBundle.primary().rawText();
            String verificationText = ocrBundle.verification().rawText();
            String thresholdText = ocrBundle.thresholdRead().rawText();

            SuspiciousCheckResult suspiciousCheck = ocrBundle.suspiciousCheck();

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
            boolean suspiciousFlatExpression =
                    looksLikeCollapsedExpression(normalizedPrimaryText);
            boolean suspiciousOriginal = suspiciousCheck.suspicious();

            boolean bothUnreadable =
                    !primaryReadable && !verificationReadable;

            boolean severeImageQualityIssue =
                    imageQualityReport.blurry()
                            || (imageQualityReport.tooSmall()
                            && imageQualityReport.lowContrast()
                            && imageQualityReport.contrastScore() < 25);

            boolean needsReview =
                    severeImageQualityIssue
                            || bothUnreadable
                            || !sameOperators
                            || !thresholdOperatorsMatch
                            || suspiciousFlatExpression;

            String analysisJson = cleanJson(openAiClient.analyzeExercise(primaryText));
            Map<String, Object> analysis;

            try {
                analysis = objectMapper.readValue(analysisJson, Map.class);
            } catch (Exception parseError) {

                analysis = Map.of(
                        "expression", "",
                        "expected", "",
                        "studentAnswer", "",
                        "correct", false
                );
            }

            boolean correct = Boolean.TRUE.equals(analysis.get("correct"));

            boolean riskyOperator =
                    hasRiskyOperator(primaryOperators)
                            || hasRiskyOperator(verificationOperators);

            boolean confidenceGateTriggered =
                    !correct && riskyOperator;

            boolean finalNeedsReview =
                    needsReview || confidenceGateTriggered;

            return Map.ofEntries(
                    Map.entry("rawText", primaryText),
                    Map.entry("verificationText", verificationText),
                    Map.entry("thresholdText", thresholdText),

                    Map.entry("normalizedRawText", normalizedPrimaryText),
                    Map.entry("normalizedVerificationText", normalizedVerificationText),
                    Map.entry("normalizedThresholdText", normalizedThresholdText),

                    Map.entry("isClearlyReadable", primaryReadable && verificationReadable),
                    Map.entry("needsTeacherReview", finalNeedsReview),
                    Map.entry("suspicious", finalNeedsReview || suspiciousOriginal),
                    Map.entry("suspiciousFlatExpression", suspiciousFlatExpression),
                    Map.entry("suspiciousReason", buildSuspiciousReason(
                            imageQualityReport,
                            primaryReadable,
                            verificationReadable,
                            sameText,
                            sameOperators,
                            thresholdOperatorsMatch,
                            suspiciousFlatExpression,
                            suspiciousCheck,
                            confidenceGateTriggered
                    )),

                    Map.entry("suggestedRawText", safe(suspiciousCheck.suggestedRawText())),

                    Map.entry("expression", safe(analysis.get("expression"))),
                    Map.entry("expected", safe(analysis.get("expected"))),
                    Map.entry("studentAnswer", safe(analysis.get("studentAnswer"))),
                    Map.entry("correct", safe(analysis.get("correct"))),

                    Map.entry("primaryOperators", primaryOperators),
                    Map.entry("verificationOperators", verificationOperators),
                    Map.entry("thresholdOperators", thresholdOperators),
                    Map.entry("sameOperators", sameOperators),
                    Map.entry("thresholdOperatorsMatch", thresholdOperatorsMatch),
                    Map.entry("riskyOperator", riskyOperator),

                    Map.entry("confidenceGateReason",
                            confidenceGateTriggered
                                    ? "Exercise is mathematically incorrect and contains a risky operator"
                                    : ""),

                    Map.entry("imageQualityReport", imageQualityReport)
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to check exercise", e);
        }
    }

    private String buildSuspiciousReason(
            ImageQualityReport imageQualityReport,
            boolean primaryReadable,
            boolean verificationReadable,
            boolean sameText,
            boolean sameOperators,
            boolean thresholdOperatorsMatch,
            boolean suspiciousFlatExpression,
            SuspiciousCheckResult suspiciousCheck,
            boolean confidenceGateTriggered
    )
    {
        StringBuilder reason = new StringBuilder();

        boolean severeImageQualityIssue =
                imageQualityReport.blurry()
                        || (imageQualityReport.tooSmall()
                        && imageQualityReport.lowContrast()
                        && imageQualityReport.contrastScore() < 25);

        if (severeImageQualityIssue) {
            reason.append("Image quality issue: ")
                    .append(imageQualityReport.reason())
                    .append(". ");
        }

        if (!primaryReadable && !verificationReadable) {
            reason.append("Both primary and verification OCR say image is not clearly readable. ");
        }

        if (!sameText) {
            reason.append("Primary and verification OCR produced different text. ");
        }

        if (!sameOperators) {
            reason.append("Operator mismatch between primary and verification OCR. ");
        }

        if (!thresholdOperatorsMatch) {
            reason.append("Operator mismatch between original/sharpened and threshold OCR. ");
        }
        if (suspiciousFlatExpression) {
            reason.append("Expression looks collapsed: equals sign exists, but left side is only a long number with no operator. ");
        }

        if (confidenceGateTriggered) {
            reason.append("Exercise is mathematically incorrect and contains a risky operator. ");
        }

        if (suspiciousCheck != null && suspiciousCheck.suspicious()) {
            reason.append("Suspicious OCR check: ")
                    .append(safe(suspiciousCheck.reason()))
                    .append(". ");
        }

        return reason.toString().trim();
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
}