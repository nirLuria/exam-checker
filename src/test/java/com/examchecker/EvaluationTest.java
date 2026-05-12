package com.examchecker;

import com.examchecker.application.CheckService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;

@SpringBootTest
public class EvaluationTest {

    @Autowired
    private CheckService checkService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void runEvaluationDataset() throws Exception {

        InputStream expectedStream =
                getClass().getResourceAsStream("/evaluation/expected-results.json");

        if (expectedStream == null) {
            throw new RuntimeException("expected-results.json not found");
        }

        List<Map<String, Object>> expectedResults =
                objectMapper.readValue(expectedStream, new TypeReference<>() {});

        int total = 0;
        int exactTextMatches = 0;
        int needsTeacherReview = 0;
        int autoResolved = 0;
        int confidentMistakes = 0;
        int correctMathMatches = 0;

        List<EvaluationCaseLog> caseLogs = new ArrayList<>();
        StringBuilder txtLog = new StringBuilder();

        txtLog.append("===== OCR EVALUATION START =====\n\n");
        System.out.println("\n===== OCR EVALUATION START =====\n");
        long evaluationStart = System.currentTimeMillis();

        for (Map<String, Object> expected : expectedResults) {
            long caseStart = System.currentTimeMillis();

            String fileName = expected.get("file").toString();
            String expectedRawText = expected.get("expectedRawText").toString();

            Boolean expectedCorrect =
                    Boolean.valueOf(expected.get("expectedCorrect").toString());

            InputStream imageStream =
                    getClass().getResourceAsStream("/evaluation/images/" + fileName);

            if (imageStream == null) {
                throw new RuntimeException("Image not found: " + fileName);
            }

            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file",
                    fileName,
                    guessContentType(fileName),
                    imageStream
            );

            Map<String, Object> result = checkWithCache(fileName, multipartFile);
            long caseDurationMs = System.currentTimeMillis() - caseStart;

            String actualRawText = safe(result.get("rawText"));
            boolean actualNeedsReview = Boolean.TRUE.equals(result.get("needsTeacherReview"));
            Boolean actualCorrect = result.get("correct") == null
                    ? null
                    : Boolean.TRUE.equals(result.get("correct"));

            boolean mathMatches = actualCorrect != null && actualCorrect.equals(expectedCorrect);

            if (mathMatches) {
                correctMathMatches++;
            }

            boolean textMatches = normalizeForComparison(actualRawText)
                    .equals(normalizeForComparison(expectedRawText));

            boolean confidentMistake =
                    !actualNeedsReview && !textMatches;

            total++;

            if (textMatches) {
                exactTextMatches++;
            }

            if (actualNeedsReview) {
                needsTeacherReview++;
            } else {
                autoResolved++;
            }

            if (confidentMistake) {
                confidentMistakes++;
            }

            EvaluationCaseLog caseLog = new EvaluationCaseLog(
                    fileName,
                    expectedRawText,
                    actualRawText,
                    textMatches,
                    actualNeedsReview,
                    safe(result.get("suspiciousReason")),
                    result.get("imageQualityReport"),
                    Boolean.TRUE.equals(result.get("thresholdOperatorsMatch")),
                    expectedCorrect,
                    actualCorrect,
                    safe(result.get("expression")),
                    result.get("expected"),
                    result.get("studentAnswer"),
                    safe(result.get("confidenceGateReason")),
                    mathMatches,
                    confidentMistake
            );

            caseLogs.add(caseLog);
            System.out.println("Duration: " + caseDurationMs + " ms");

            appendCaseToTxtLog(txtLog, caseLog);
            txtLog.append("Duration: ").append(caseDurationMs).append(" ms\n");
            printCase(caseLog);
        }

        EvaluationSummaryLog summaryLog = new EvaluationSummaryLog(
                total,
                exactTextMatches,
                percentNumber(exactTextMatches, total),
                needsTeacherReview,
                percentNumber(needsTeacherReview, total),
                autoResolved,
                percentNumber(autoResolved, total),
                confidentMistakes,
                correctMathMatches,
                percentNumber(correctMathMatches, total)
        );

        FullEvaluationLog fullLog = new FullEvaluationLog(summaryLog, caseLogs);

        long evaluationDurationMs = System.currentTimeMillis() - evaluationStart;
        appendSummaryToTxtLog(txtLog, summaryLog);
        printSummary(summaryLog);
        System.out.println("Total duration: " + evaluationDurationMs + " ms");
        System.out.println("Average per case: " + (total == 0 ? 0 : evaluationDurationMs / total) + " ms");
        txtLog.append("Total duration: ").append(evaluationDurationMs).append(" ms\n");
        txtLog.append("Average per case: ")
                .append(total == 0 ? 0 : evaluationDurationMs / total)
                .append(" ms\n");

        Path outputDir = Path.of("target", "evaluation");
        Files.createDirectories(outputDir);

        Files.writeString(
                outputDir.resolve("evaluation-log.txt"),
                txtLog.toString(),
                StandardCharsets.UTF_8
        );

        objectMapper.writeValue(
                outputDir.resolve("evaluation-results.json").toFile(),
                fullLog
        );
    }

    private Map<String, Object> checkWithCache(
            String fileName,
            MockMultipartFile multipartFile
    ) throws Exception {

        byte[] bytes = multipartFile.getBytes();
        String hash = sha256(bytes);

        Path cacheDir = Path.of("target", "evaluation", "cache");
        Files.createDirectories(cacheDir);

        String safeFileName = fileName
                .replace("/", "_")
                .replace("\\", "_")
                .replace(".", "_");

        Path cacheFile = cacheDir.resolve(safeFileName + "-" + hash + ".json");

        if (Files.exists(cacheFile)) {
            return objectMapper.readValue(
                    Files.readString(cacheFile, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {}
            );
        }

        Map<String, Object> result = checkService.check(multipartFile);
        objectMapper.writeValue(cacheFile.toFile(), result);

        return result;
    }

    private String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);

        StringBuilder sb = new StringBuilder();

        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    private void appendCaseToTxtLog(StringBuilder txtLog, EvaluationCaseLog log) {
        txtLog.append("File: ").append(log.fileName()).append("\n");
        txtLog.append("Expected text: ").append(log.expectedText()).append("\n");
        txtLog.append("Actual text:   ").append(log.actualText()).append("\n");
        txtLog.append("Text match:    ").append(log.textMatch()).append("\n");
        txtLog.append("Needs review:  ").append(log.needsTeacherReview()).append("\n");
        txtLog.append("Reason: ").append(log.reason()).append("\n");
        txtLog.append("Image quality: ").append(log.imageQuality()).append("\n");
        txtLog.append("Threshold operators match: ").append(log.thresholdOperatorsMatch()).append("\n");
        txtLog.append("Expected correct: ").append(log.expectedCorrect()).append("\n");
        txtLog.append("Actual correct:   ").append(log.actualCorrect()).append("\n");
        txtLog.append("Expression: ").append(log.expression()).append("\n");
        txtLog.append("Expected value: ").append(log.expectedValue()).append("\n");
        txtLog.append("Student answer: ").append(log.studentAnswer()).append("\n");
        txtLog.append("Confidence gate: ").append(log.confidenceGate()).append("\n");
        txtLog.append("Math match: ").append(log.mathMatch()).append("\n");
        txtLog.append("Confident mistake: ").append(log.confidentMistake()).append("\n");
        txtLog.append("--------------------------------\n");
    }

    private void printCase(EvaluationCaseLog log) {
        System.out.println("File: " + log.fileName());
        System.out.println("Expected text: " + log.expectedText());
        System.out.println("Actual text:   " + log.actualText());
        System.out.println("Text match:    " + log.textMatch());
        System.out.println("Needs review:  " + log.needsTeacherReview());
        System.out.println("Reason: " + log.reason());
        System.out.println("Image quality: " + log.imageQuality());
        System.out.println("Threshold operators match: " + log.thresholdOperatorsMatch());
        System.out.println("Expected correct: " + log.expectedCorrect());
        System.out.println("Actual correct:   " + log.actualCorrect());
        System.out.println("Expression: " + log.expression());
        System.out.println("Expected value: " + log.expectedValue());
        System.out.println("Student answer: " + log.studentAnswer());
        System.out.println("Confidence gate: " + log.confidenceGate());
        System.out.println("Math match: " + log.mathMatch());
        System.out.println("Confident mistake: " + log.confidentMistake());
        System.out.println("--------------------------------");
    }

    private void appendSummaryToTxtLog(StringBuilder txtLog, EvaluationSummaryLog summary) {
        txtLog.append("\n===== OCR EVALUATION SUMMARY =====\n");
        txtLog.append("Total: ").append(summary.total()).append("\n");
        txtLog.append("Exact OCR matches: ")
                .append(summary.exactOcrMatches()).append("/").append(summary.total())
                .append(" = ").append(summary.exactOcrMatchesPercent()).append("%\n");
        txtLog.append("Needs teacher review: ")
                .append(summary.needsTeacherReview()).append("/").append(summary.total())
                .append(" = ").append(summary.needsTeacherReviewPercent()).append("%\n");
        txtLog.append("Auto resolved: ")
                .append(summary.autoResolved()).append("/").append(summary.total())
                .append(" = ").append(summary.autoResolvedPercent()).append("%\n");
        txtLog.append("Confident mistakes: ").append(summary.confidentMistakes()).append("\n");
        txtLog.append("Math correctness matches: ")
                .append(summary.mathCorrectnessMatches()).append("/").append(summary.total())
                .append(" = ").append(summary.mathCorrectnessMatchesPercent()).append("%\n");
        txtLog.append("==================================\n");
    }

    private void printSummary(EvaluationSummaryLog summary) {

        System.out.println("\n===== OCR EVALUATION SUMMARY =====");
        System.out.println("Total: " + summary.total());
        System.out.println("Exact OCR matches: " + summary.exactOcrMatches() + "/" + summary.total()
                + " = " + summary.exactOcrMatchesPercent() + "%");
        System.out.println("Needs teacher review: " + summary.needsTeacherReview() + "/" + summary.total()
                + " = " + summary.needsTeacherReviewPercent() + "%");
        System.out.println("Auto resolved: " + summary.autoResolved() + "/" + summary.total()
                + " = " + summary.autoResolvedPercent() + "%");
        System.out.println("Confident mistakes: " + summary.confidentMistakes());
        System.out.println("Math correctness matches: " + summary.mathCorrectnessMatches() + "/" + summary.total()
                + " = " + summary.mathCorrectnessMatchesPercent() + "%");
        System.out.println("==================================\n");
    }

    private String normalizeForComparison(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("×", "*")
                .replace("x", "*")
                .replace("X", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .replaceAll("\\s+", "")
                .trim();
    }

    record EvaluationCaseLog(
            String fileName,
            String expectedText,
            String actualText,
            boolean textMatch,
            boolean needsTeacherReview,
            String reason,
            Object imageQuality,
            boolean thresholdOperatorsMatch,
            Boolean expectedCorrect,
            Boolean actualCorrect,
            String expression,
            Object expectedValue,
            Object studentAnswer,
            String confidenceGate,
            boolean mathMatch,
            boolean confidentMistake
    ) {}

    record EvaluationSummaryLog(
            int total,
            int exactOcrMatches,
            double exactOcrMatchesPercent,
            int needsTeacherReview,
            double needsTeacherReviewPercent,
            int autoResolved,
            double autoResolvedPercent,
            int confidentMistakes,
            int mathCorrectnessMatches,
            double mathCorrectnessMatchesPercent
    ) {}

    record FullEvaluationLog(
            EvaluationSummaryLog summary,
            List<EvaluationCaseLog> cases
    ) {}

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private double percentNumber(int value, int total) {
        if (total == 0) {
            return 0.0;
        }

        return Math.round((value * 1000.0) / total) / 10.0;
    }

    private String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();

        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "image/png";
    }
}