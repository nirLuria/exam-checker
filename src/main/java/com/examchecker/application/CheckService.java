package com.examchecker.application;

import com.examchecker.infrastructure.OcrService;
import com.examchecker.infrastructure.OpenAiClient;
import com.examchecker.service.MathTextNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class CheckService {

    private final OcrService ocrService;
    private final OpenAiClient openAiClient;
    private final MathTextNormalizer mathTextNormalizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CheckService(
            OcrService ocrService,
            OpenAiClient openAiClient,
            MathTextNormalizer mathTextNormalizer
    ) {
        this.ocrService = ocrService;
        this.openAiClient = openAiClient;
        this.mathTextNormalizer = mathTextNormalizer;
    }

    public Map<String, Object> check(MultipartFile file) {
        try {
            String ocrJson = cleanJson(ocrService.extractText(file));

            Map<String, Object> wrapper = objectMapper.readValue(ocrJson, Map.class);

            Map<String, Object> primary = (Map<String, Object>) wrapper.get("primary");
            Map<String, Object> verification = (Map<String, Object>) wrapper.get("verification");
            Map<String, Object> thresholdRead = (Map<String, Object>) wrapper.get("thresholdRead");
            Map<String, Object> suspiciousCheck = (Map<String, Object>) wrapper.get("suspiciousCheck");

            String primaryText = safe(primary.get("rawText")).toString();
            String verificationText = safe(verification.get("rawText")).toString();

            String normalizedPrimaryText = mathTextNormalizer.normalize(primaryText);
            String normalizedVerificationText = mathTextNormalizer.normalize(verificationText);
            String primaryOperators = extractOperators(normalizedPrimaryText);
            String verificationOperators = extractOperators(normalizedVerificationText);
            String thresholdText = safe(thresholdRead.get("rawText")).toString();
            String normalizedThresholdText = mathTextNormalizer.normalize(thresholdText);
            String thresholdOperators = extractOperators(normalizedThresholdText);

            boolean sameOperators = primaryOperators.equals(verificationOperators);
            boolean thresholdOperatorsMatch =
                    primaryOperators.equals(thresholdOperators);

            boolean primaryReadable = Boolean.TRUE.equals(primary.get("isClearlyReadable"));
            boolean verificationReadable = Boolean.TRUE.equals(verification.get("isClearlyReadable"));

            boolean sameText = normalizedPrimaryText.equals(normalizedVerificationText);

            boolean suspiciousOriginal = suspiciousCheck != null
                    && Boolean.TRUE.equals(suspiciousCheck.get("suspicious"));

            boolean needsReview = !primaryReadable
                    || !verificationReadable
                    || !thresholdOperatorsMatch
                    || !sameText;

            if (needsReview) {
                return Map.of(
                        "rawText", primaryText,
                        "verificationText", verificationText,
                        "normalizedRawText", normalizedPrimaryText,
                        "normalizedVerificationText", normalizedVerificationText,
                        "isClearlyReadable", false,
                        "needsTeacherReview", true,
                        "suspicious", suspiciousOriginal,
                        "suspiciousReason", suspiciousCheck == null ? "" : safe(suspiciousCheck.get("reason")),
                        "suggestedRawText", suspiciousCheck == null ? "" : safe(suspiciousCheck.get("suggestedRawText"))
                );
            }

            String analysisJson = cleanJson(openAiClient.analyzeExercise(primaryText));
            Map<String, Object> analysis = objectMapper.readValue(analysisJson, Map.class);

            boolean correct = Boolean.TRUE.equals(analysis.get("correct"));

            boolean riskyOperator =
                    hasRiskyOperator(primaryOperators)
                            || hasRiskyOperator(verificationOperators);

            boolean finalNeedsReview =
                    !correct && riskyOperator;

            return Map.ofEntries(
                    Map.entry("rawText", primaryText),
                    Map.entry("verificationText", verificationText),
                    Map.entry("normalizedRawText", normalizedPrimaryText),
                    Map.entry("normalizedVerificationText", normalizedVerificationText),
                    Map.entry("isClearlyReadable", true),
                    Map.entry("needsTeacherReview", finalNeedsReview),
                    Map.entry("suspicious", false),
                    Map.entry("expression", safe(analysis.get("expression"))),
                    Map.entry("expected", safe(analysis.get("expected"))),
                    Map.entry("studentAnswer", safe(analysis.get("studentAnswer"))),
                    Map.entry("correct", safe(analysis.get("correct"))),
                    Map.entry("primaryOperators", primaryOperators),
                    Map.entry("verificationOperators", verificationOperators),
                    Map.entry("riskyOperator", riskyOperator),
                    Map.entry("thresholdOperators", thresholdOperators),
                    Map.entry("thresholdOperatorsMatch", thresholdOperatorsMatch),
                    Map.entry("thresholdText", thresholdText),
                    Map.entry("sameOperators", sameOperators),
                    Map.entry("confidenceGateReason",
                            finalNeedsReview
                                    ? "Exercise is mathematically incorrect and contains a risky operator"
                                    : "")
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to check exercise", e);
        }
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

        return text
                .replaceAll("[0-9\\s=()]", "");
    }
    private boolean hasRiskyOperator(String operators) {
        if (operators == null) {
            return false;
        }

        return operators.contains("^")
                || operators.contains("*")
                || operators.contains("×")
                || operators.contains("x")
                || operators.contains("-");
    }
}