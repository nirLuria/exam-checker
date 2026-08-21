package com.examchecker.infrastructure.ocr.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OcrBundleParser {

    private final ObjectMapper objectMapper;

    public OcrBundleParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OcrBundleResult parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new OcrBundleParseException("OCR output is empty");
        }

        try {
            JsonNode root = objectMapper.readTree(cleanJson(rawOutput));
            if (root == null || !root.isObject()) {
                throw new OcrBundleParseException("OCR output must be a JSON object");
            }

            return new OcrBundleResult(
                    toOcrReading(requiredObject(root, "primary"), "primary"),
                    toOcrReading(requiredObject(root, "verification"), "verification"),
                    toOcrReading(requiredObject(root, "thresholdRead"), "thresholdRead"),
                    toSuspiciousCheck(requiredObject(root, "suspiciousCheck"))
            );
        } catch (OcrBundleParseException e) {
            throw e;
        } catch (Exception e) {
            throw new OcrBundleParseException("Failed to parse OCR bundle JSON", e);
        }
    }

    private OcrReading toOcrReading(JsonNode node, String fieldName) {
        return new OcrReading(
                requiredText(node, "rawText", fieldName),
                requiredBoolean(node, "isClearlyReadable", fieldName)
        );
    }

    private SuspiciousCheckResult toSuspiciousCheck(JsonNode node) {
        return new SuspiciousCheckResult(
                requiredBoolean(node, "suspicious", "suspiciousCheck"),
                requiredText(node, "reason", "suspiciousCheck"),
                requiredText(node, "suggestedRawText", "suspiciousCheck")
        );
    }

    private JsonNode requiredObject(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isObject()) {
            throw new OcrBundleParseException(fieldName + " must be a JSON object");
        }
        return value;
    }

    private String requiredText(JsonNode parent, String fieldName, String parentName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isTextual()) {
            throw new OcrBundleParseException(parentName + "." + fieldName + " must be text");
        }
        return value.textValue();
    }

    private boolean requiredBoolean(JsonNode parent, String fieldName, String parentName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isBoolean()) {
            throw new OcrBundleParseException(parentName + "." + fieldName + " must be boolean");
        }
        return value.booleanValue();
    }

    private String cleanJson(String rawOutput) {
        String cleaned = rawOutput.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring("```json".length()).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring("```".length()).trim();
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }
}
