package com.examchecker.infrastructure;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiClient {

    private static final String OPENAI_URL = "https://api.openai.com/v1/responses";
    private static final String MODEL = "gpt-4.1-mini";

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey = System.getenv("OPENAI_API_KEY");

    // ================= OCR PROMPTS =================

    private static final String PRIMARY_PROMPT = """
            Your only task is to transcribe the handwritten math exercise from the image.

            Copy exactly what is written.
            Do not solve.
            Do not calculate.
            Do not correct mistakes.
            If any digit or symbol is unclear, write [?].
            If anything is unclear, set isClearlyReadable=false.

            Digits like 1, 7, 8, 9 can look similar in handwriting.
            If a digit could reasonably be another digit, do not assume it is correct.
            Set isClearlyReadable=false.

            Return raw JSON only:
            {
              "rawText": "...",
              "isClearlyReadable": true
            }
            """;

    private static final String VERIFICATION_PROMPT = """
            Carefully re-read the handwritten math exercise from the image.

            Focus on:
            - missing leading digits (17 vs 7)
            - similar digits (1, 7, 9, 3)
            - + vs -
            - the result after =

            Copy exactly what is written.
            If any part is uncertain, use [?] and set isClearlyReadable=false.
            Do not guess.

            Double-check each digit carefully.
            Especially verify that 8 is not misread as 1 or 3.
            If unsure about any digit, mark isClearlyReadable=false.
            
            Return raw JSON only:
            {
              "rawText": "...",
              "isClearlyReadable": true
            }
            """;

    // ================= ANALYSIS PROMPT =================

    private static final String ANALYSIS_PROMPT = """
            You are analyzing a math exercise.

            Given this expression:
            %s

            Tasks:
            1. Extract the left side expression (before =)
            2. Extract the student answer (after =)
            3. Calculate the correct result using proper math rules
            4. Compare

            Rules:
            - studentAnswer must be a number or null
            - expected must be a number
            - correct must be true/false
            - Do not guess missing values

            Return JSON only:
            {
              "expression": "...",
              "expected": number,
              "studentAnswer": number,
              "correct": true
            }
            """;

    // ================= PUBLIC METHODS =================

    public String extractRawTextPrimary(String base64Image) {
        return sendImageRequest(base64Image, PRIMARY_PROMPT);
    }

    public String extractRawTextVerification(String base64Image) {
        return sendImageRequest(base64Image, VERIFICATION_PROMPT);
    }

    public String analyzeExercise(String rawText) {
        String prompt = ANALYSIS_PROMPT.formatted(rawText);

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "input", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of(
                                                "type", "input_text",
                                                "text", prompt
                                        )
                                )
                        )
                )
        );

        return sendRequest(body);
    }

    // ================= INTERNAL =================

    private String sendImageRequest(String base64Image, String prompt) {
        String dataUrl = "data:image/png;base64," + base64Image;

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "input", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of(
                                                "type", "input_text",
                                                "text", prompt
                                        ),
                                        Map.of(
                                                "type", "input_image",
                                                "image_url", dataUrl
                                        )
                                )
                        )
                )
        );

        return sendRequest(body);
    }

    public String verifySuspiciousDigits(String base64Image, String rawText) {
        String prompt = """
            We already transcribed this handwritten math exercise as:
            %s

            Your task is ONLY to verify suspicious digits in the image.

            Focus especially on digits that were read as 1.
            Check whether any 1 could actually be 8, 7, or another digit.

            Do not solve the exercise.
            Do not calculate.
            Do not rewrite the full expression unless necessary.

            Return raw JSON only:
            {
              "suspicious": true,
              "reason": "...",
              "suggestedRawText": "..."
            }

            If everything is clearly correct, return:
            {
              "suspicious": false,
              "reason": "",
              "suggestedRawText": "%s"
            }
            """.formatted(rawText, rawText);

        return sendImageRequest(base64Image, prompt);
    }

    private String sendRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(OPENAI_URL, request, Map.class);

        return extractTextFromResponse(response.getBody());
    }

    private String extractTextFromResponse(Map response) {
        try {
            List output = (List) response.get("output");
            Map firstOutput = (Map) output.get(0);

            List contentList = (List) firstOutput.get("content");
            Map firstContent = (Map) contentList.get(0);

            return (String) firstContent.get("text");

        } catch (Exception e) {
            throw new RuntimeException("Failed parsing OpenAI response: " + response, e);
        }
    }
}