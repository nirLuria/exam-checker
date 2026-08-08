package com.examchecker.infrastructure.ocr.googlevision;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.util.List;

@Service
public class GoogleVisionOcrService {

    private final GoogleVisionProperties properties;

    public GoogleVisionOcrService(
            GoogleVisionProperties properties
    ) {
        this.properties = properties;
    }

    public String extractText(MultipartFile image) {

        try {

            GoogleCredentials credentials =
                    GoogleCredentials.fromStream(
                            new FileInputStream(
                                    properties.credentialsPath()
                            )
                    );

            ImageAnnotatorSettings settings =
                    ImageAnnotatorSettings.newBuilder()
                            .setCredentialsProvider(() -> credentials)
                            .build();

            try (ImageAnnotatorClient vision =
                         ImageAnnotatorClient.create(settings)) {

                ByteString imgBytes =
                        ByteString.copyFrom(image.getBytes());

                Image img = Image.newBuilder()
                        .setContent(imgBytes)
                        .build();

                Feature feature = Feature.newBuilder()
                        .setType(Feature.Type.TEXT_DETECTION)
                        .build();

                AnnotateImageRequest request =
                        AnnotateImageRequest.newBuilder()
                                .addFeatures(feature)
                                .setImage(img)
                                .build();

                BatchAnnotateImagesResponse response =
                        vision.batchAnnotateImages(List.of(request));

                AnnotateImageResponse imageResponse =
                        response.getResponses(0);

                String text =
                        imageResponse.getFullTextAnnotation().getText();

                return buildOcrBundle(text);

            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Google Vision OCR failed: " + e.getMessage(), e);
        }
    }

    private String buildOcrBundle(String text) {

        String escaped = text
                .replace("\"", "\\\"")
                .replace("\n", " ");

        return """
                {
                  "primary": {
                    "rawText": "%s",
                    "isClearlyReadable": true
                  },
                  "verification": {
                    "rawText": "%s",
                    "isClearlyReadable": true
                  },
                  "thresholdRead": {
                    "rawText": "%s",
                    "isClearlyReadable": true
                  },
                  "suspiciousCheck": {
                    "suspicious": false,
                    "reason": "",
                    "suggestedRawText": ""
                  }
                }
                """.formatted(
                escaped,
                escaped,
                escaped
        );
    }
}