package com.examchecker.infrastructure.ocr.core;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MultiEngineOcrService {

    private final List<OcrEngine> engines;
    private final OcrBundleParser bundleParser;

    public MultiEngineOcrService(List<OcrEngine> engines, OcrBundleParser bundleParser) {
        this.engines = List.copyOf(engines);
        this.bundleParser = bundleParser;
        validateUniqueEngineNames(this.engines);
    }

    public List<OcrEngineResult> extractWithAllEngines(MultipartFile file) {
        List<OcrEngineResult> results = new ArrayList<>();

        engines.stream()
                .sorted(Comparator.comparing(engine -> engine.metadata().engineName().name()))
                .forEach(engine -> results.add(runEngine(engine, file)));

        return List.copyOf(results);
    }

    public OcrEngineResult extractWithEngine(OcrEngineName engineName, MultipartFile file) {
        OcrEngine engine = engines.stream()
                .filter(candidate -> candidate.metadata().engineName() == engineName)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing OCR engine: " + engineName));

        return runEngine(engine, file);
    }

    private OcrEngineResult runEngine(OcrEngine engine, MultipartFile file) {
        OcrEngineMetadata metadata = engine.metadata();
        long startTime = System.nanoTime();
        String rawOutput = "";

        try {
            rawOutput = engine.extractRaw(file);
            OcrBundleResult bundle = bundleParser.parse(rawOutput);

            return OcrEngineResult.success(
                    metadata,
                    bundle,
                    rawOutput,
                    null,
                    elapsedMillis(startTime)
            );
        } catch (Exception e) {
            return OcrEngineResult.failed(
                    metadata,
                    detectFailureType(e),
                    rawOutput,
                    extractFailureMessage(e),
                    elapsedMillis(startTime)
            );
        }
    }

    private long elapsedMillis(long startTime) {
        return Math.max(0L, (System.nanoTime() - startTime) / 1_000_000L);
    }

    private void validateUniqueEngineNames(List<OcrEngine> configuredEngines) {
        Set<OcrEngineName> names = EnumSet.noneOf(OcrEngineName.class);
        for (OcrEngine engine : configuredEngines) {
            OcrEngineName name = engine.metadata().engineName();
            if (!names.add(name)) {
                throw new IllegalArgumentException("Duplicate OCR engine: " + name);
            }
        }
    }

    private OcrEngineFailureType detectFailureType(Exception exception) {
        if (exception instanceof OcrBundleParseException) {
            return OcrEngineFailureType.PARSE_ERROR;
        }

        Throwable root = rootCause(exception);
        if (root instanceof SocketTimeoutException || root instanceof HttpTimeoutException) {
            return OcrEngineFailureType.TIMEOUT;
        }
        if (exception instanceof ResourceAccessException) {
            return OcrEngineFailureType.NETWORK_ERROR;
        }
        if (root instanceof HttpClientErrorException.TooManyRequests) {
            return OcrEngineFailureType.RATE_LIMIT;
        }
        if (root instanceof HttpClientErrorException.Unauthorized
                || root instanceof HttpClientErrorException.Forbidden) {
            return OcrEngineFailureType.AUTH_ERROR;
        }

        String message = extractFailureMessage(exception).toLowerCase(Locale.ROOT);
        if (message.contains("resource_exhausted")
                || message.contains("prepayment credits are depleted")) {
            return OcrEngineFailureType.QUOTA_EXCEEDED;
        }
        if (message.contains("429 too many requests")) {
            return OcrEngineFailureType.RATE_LIMIT;
        }
        if (message.contains("401 unauthorized") || message.contains("403 forbidden")) {
            return OcrEngineFailureType.AUTH_ERROR;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return OcrEngineFailureType.TIMEOUT;
        }

        return OcrEngineFailureType.UNKNOWN;
    }

    private String extractFailureMessage(Exception exception) {
        Throwable root = rootCause(exception);
        if (root instanceof HttpClientErrorException httpException) {
            String responseBody = httpException.getResponseBodyAsString();
            return responseBody.isBlank() ? safeMessage(root) : responseBody;
        }
        return safeMessage(root);
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "" : throwable.getMessage();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
