package com.examchecker.infrastructure.ocr.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "exam-checker.ocr.orchestration")
public class OcrOrchestrationProperties {

    private int maxConcurrency = 3;
    private Duration engineTimeout = Duration.ofSeconds(30);

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be positive");
        }
        this.maxConcurrency = maxConcurrency;
    }

    public Duration getEngineTimeout() {
        return engineTimeout;
    }

    public void setEngineTimeout(Duration engineTimeout) {
        if (engineTimeout == null || engineTimeout.isZero() || engineTimeout.isNegative()) {
            throw new IllegalArgumentException("engineTimeout must be positive");
        }
        this.engineTimeout = engineTimeout;
    }
}
