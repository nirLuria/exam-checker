package com.examchecker.infrastructure.ocr.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrBundleParserTest {

    private final OcrBundleParser parser = new OcrBundleParser(new ObjectMapper());

    @Test
    void parsesCompleteBundle() {
        OcrBundleResult bundle = parser.parse(validRawOutput());

        assertEquals("5+3=8", bundle.primary().rawText());
        assertTrue(bundle.primary().clearlyReadable());
        assertEquals("5+3=8", bundle.verification().rawText());
        assertFalse(bundle.suspiciousCheck().suspicious());
    }

    @Test
    void parsesJsonCodeFence() {
        OcrBundleResult bundle = parser.parse("```json\n" + validRawOutput() + "\n```");

        assertEquals("5+3=8", bundle.thresholdRead().rawText());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(OcrBundleParseException.class, () -> parser.parse("{not-json}"));
    }

    @Test
    void rejectsMissingRequiredReading() {
        String missingVerification = validRawOutput().replace(
                "\"verification\": {\"rawText\": \"5+3=8\", \"isClearlyReadable\": true},",
                ""
        );

        OcrBundleParseException exception = assertThrows(
                OcrBundleParseException.class,
                () -> parser.parse(missingVerification)
        );

        assertTrue(exception.getMessage().contains("verification"));
    }

    static String validRawOutput() {
        return """
                {
                  "primary": {"rawText": "5+3=8", "isClearlyReadable": true},
                  "verification": {"rawText": "5+3=8", "isClearlyReadable": true},
                  "thresholdRead": {"rawText": "5+3=8", "isClearlyReadable": true},
                  "suspiciousCheck": {
                    "suspicious": false,
                    "reason": "",
                    "suggestedRawText": ""
                  }
                }
                """;
    }
}
