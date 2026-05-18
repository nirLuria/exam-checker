package com.examchecker.infrastructure.ocr.gemini;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

@Service
public class GeminiOcrService {

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.builder().build();

    public GeminiOcrService(GeminiProperties properties) {
        this.properties = properties;
    }

    public String extractText(MultipartFile image) {

        try {

            byte[] bytes = image.getBytes();

            String base64 = Base64.getEncoder().encodeToString(bytes);

            String prompt = """
                    You are an OCR engine for handwritten math exercises.

                    Return ONLY valid JSON.

                    Use exactly this structure:

                    {
                      "primary": {
                        "rawText": "...",
                        "isClearlyReadable": true
                      },
                      "verification": {
                        "rawText": "...",
                        "isClearlyReadable": true
                      },
                      "thresholdRead": {
                        "rawText": "...",
                        "isClearlyReadable": true
                      },
                      "suspiciousCheck": {
                        "suspicious": false,
                        "reason": "",
                        "suggestedRawText": ""
                      }
                    }
                    """;

            Map<String, Object> request = Map.of(
                    "contents", new Object[]{
                            Map.of(
                                    "parts", new Object[]{
                                            Map.of("text", prompt),
                                            Map.of(
                                                    "inline_data",
                                                    Map.of(
                                                            "mime_type", image.getContentType(),
                                                            "data", base64
                                                    )
                                            )
                                    }
                            )
                    }
            );

            String url = properties.baseUrl()
                    + "/models/"
                    + properties.model()
                    + ":generateContent?key="
                    + properties.apiKey();

            String response = restClient.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            return extractTextFromGeminiResponse(response);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Gemini OCR failed: " + e.getMessage(), e);
        }
    }

    private String extractTextFromGeminiResponse(String response) {
        try {
            Map<String, Object> root = objectMapper.readValue(response, Map.class);

            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) root.get("candidates");

            Map<String, Object> firstCandidate = candidates.get(0);

            Map<String, Object> content =
                    (Map<String, Object>) firstCandidate.get("content");

            List<Map<String, Object>> parts =
                    (List<Map<String, Object>>) content.get("parts");

            String text = parts.get(0).get("text").toString();

            return text
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

        } catch (Exception e) {
            throw new RuntimeException("Failed extracting Gemini text from response: " + response, e);
        }
    }
}