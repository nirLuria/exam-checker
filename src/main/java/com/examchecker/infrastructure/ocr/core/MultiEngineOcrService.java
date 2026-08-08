package com.examchecker.infrastructure.ocr.core;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MultiEngineOcrService {

    private final List<OcrEngine> engines;

    public MultiEngineOcrService(List<OcrEngine> engines) {
        this.engines = engines;
    }



    public List<OcrEngineBundleResult> extractWithAllEngines(MultipartFile file) {
        List<OcrEngineBundleResult> results = new ArrayList<>();

        engines.stream()
                .sorted(Comparator.comparing(engine -> engine.name().name()))
                .forEach(engine -> results.add(runEngine(engine, file)));

        return results;
    }

    private OcrEngineBundleResult runEngine(OcrEngine engine, MultipartFile file) {
        long startTime = System.currentTimeMillis();

        System.out.println("Running engine through MultiEngineOcrService: " + engine.name());

        try {
            OcrBundleResult bundleResult = engine.extract(file);
            long durationMs = System.currentTimeMillis() - startTime;

            return OcrEngineBundleResult.success(
                    engine.name(),
                    bundleResult,
                    durationMs
            );

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;

            System.out.println("CAUGHT ENGINE ERROR: " + engine.name() + " | " + e.getClass().getName());

            return OcrEngineBundleResult.failed(
                    engine.name(),
                    detectFailureType(e),
                    extractFailureMessage(e),
                    durationMs
            );
        }
    }

    public OcrEngineBundleResult extractWithEngine(
            OcrEngineName engineName,
            MultipartFile file
    ) {
        OcrEngine engine = engines.stream()
                .filter(e -> e.name() == engineName)
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Missing OCR engine: " + engineName)
                );

        return runEngine(engine, file);
    }

    private OcrEngineFailureType detectFailureType(Exception e) {
        Throwable root = rootCause(e);

        if (root instanceof HttpClientErrorException.TooManyRequests) {
            return OcrEngineFailureType.QUOTA_EXCEEDED;
        }

        String message = extractFailureMessage(e);

        if (message.contains("RESOURCE_EXHAUSTED")
                || message.contains("prepayment credits are depleted")
                || message.contains("429 Too Many Requests")) {
            return OcrEngineFailureType.QUOTA_EXCEEDED;
        }

        if (message.contains("401 Unauthorized")
                || message.contains("403 Forbidden")) {
            return OcrEngineFailureType.AUTH_ERROR;
        }

        if (message.toLowerCase().contains("timeout")) {
            return OcrEngineFailureType.TIMEOUT;
        }

        return OcrEngineFailureType.UNKNOWN;
    }

    private String extractFailureMessage(Exception e) {
        Throwable root = rootCause(e);

        if (root instanceof HttpClientErrorException httpException) {
            return httpException.getResponseBodyAsString();
        }

        String message = root.getMessage();
        return message == null ? "" : message;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }
}