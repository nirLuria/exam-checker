package com.examchecker.infrastructure.ocr.googlevision;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "googlevision")
public record GoogleVisionProperties(
        String credentialsPath
) {
}