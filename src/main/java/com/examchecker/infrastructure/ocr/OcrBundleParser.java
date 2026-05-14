package com.examchecker.infrastructure.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OcrBundleParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OcrBundleResult parse(String json) {
        try {
            String cleanedJson = cleanJson(json);

            Map<String, Object> wrapper =
                    objectMapper.readValue(cleanedJson, Map.class);

            return new OcrBundleResult(
                    toOcrReading((Map<String, Object>) wrapper.get("primary")),
                    toOcrReading((Map<String, Object>) wrapper.get("verification")),
                    toOcrReading((Map<String, Object>) wrapper.get("thresholdRead")),
                    toSuspiciousCheck((Map<String, Object>) wrapper.get("suspiciousCheck"))
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OCR bundle JSON: " + json, e);
        }
    }

    private OcrReading toOcrReading(Map<String, Object> map) {
        if (map == null) {
            return new OcrReading("", false);
        }

        return new OcrReading(
                safe(map.get("rawText")),
                Boolean.TRUE.equals(map.get("isClearlyReadable"))
        );
    }

    private SuspiciousCheckResult toSuspiciousCheck(Map<String, Object> map) {
        if (map == null) {
            return new SuspiciousCheckResult(false, "", "");
        }

        return new SuspiciousCheckResult(
                Boolean.TRUE.equals(map.get("suspicious")),
                safe(map.get("reason")),
                safe(map.get("suggestedRawText"))
        );
    }

    private String cleanJson(String json) {
        if (json == null) {
            return "";
        }

        return json
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}