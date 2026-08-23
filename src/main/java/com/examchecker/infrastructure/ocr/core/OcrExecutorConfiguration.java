package com.examchecker.infrastructure.ocr.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class OcrExecutorConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService ocrExecutorService(OcrOrchestrationProperties properties) {
        return Executors.newFixedThreadPool(properties.getMaxConcurrency());
    }
}
