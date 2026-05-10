package com.examchecker.infrastructure;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@Primary
@Service
public class OpenAiOcrService implements OcrService {

    private final OpenAiClient openAiClient;
    private final ImagePreprocessingService imagePreprocessingService;

    public OpenAiOcrService(OpenAiClient openAiClient,
                            ImagePreprocessingService imagePreprocessingService) {
        this.openAiClient = openAiClient;
        this.imagePreprocessingService = imagePreprocessingService;
    }

    @Override
    public String extractText(MultipartFile file) {

        try {

            byte[] originalImage = file.getBytes();

            ImagePreprocessingService.ProcessedImage processed =
                    imagePreprocessingService.preprocess(originalImage);

            String sharpenedBase64 =
                    Base64.getEncoder()
                            .encodeToString(processed.sharpenedImage());

            String thresholdBase64 =
                    Base64.getEncoder()
                            .encodeToString(processed.thresholdImage());


            String primary =
                    openAiClient.extractRawTextPrimary(sharpenedBase64);

            String verification =
                    openAiClient.extractRawTextVerification(sharpenedBase64);

            String thresholdRead =
                    openAiClient.extractRawTextPrimary(thresholdBase64);

            String suspiciousCheck = openAiClient.verifySuspiciousDigits( sharpenedBase64, primary );

            return """
            {
                "primary": %s,
                "verification": %s,
                "thresholdRead": %s,
                "suspiciousCheck": %s
            }
            """.formatted(
                    primary,
                    verification,
                    thresholdRead,
                    suspiciousCheck
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to process image with OpenAI",
                    e
            );
        }
    }
}