package com.examchecker.infrastructure.ocr.core;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import com.examchecker.question.QuestionPackage;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class MultiEngineOcrService {

    private final List<OcrEngine> engines;
    private final OcrBundleParser bundleParser;
    private final Clock clock;
    private final Supplier<UUID> runIdSupplier;
    private final ExecutorService executorService;
    private final Duration engineTimeout;

    @Autowired
    public MultiEngineOcrService(
            List<OcrEngine> engines,
            OcrBundleParser bundleParser,
            ExecutorService executorService,
            OcrOrchestrationProperties properties
    ) {
        this(
                engines,
                bundleParser,
                executorService,
                properties.getEngineTimeout(),
                Clock.systemUTC(),
                UUID::randomUUID
        );
    }

    MultiEngineOcrService(
            List<OcrEngine> engines,
            OcrBundleParser bundleParser,
            ExecutorService executorService,
            Duration engineTimeout,
            Clock clock,
            Supplier<UUID> runIdSupplier
    ) {
        this.engines = List.copyOf(engines);
        this.bundleParser = bundleParser;
        this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
        this.engineTimeout = requirePositive(engineTimeout, "engineTimeout");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.runIdSupplier = Objects.requireNonNull(runIdSupplier, "runIdSupplier must not be null");
        validateUniqueEngineNames(this.engines);
    }

    public List<OcrEngineResult> extractWithAllEngines(QuestionPackage questionPackage) {
        Objects.requireNonNull(questionPackage, "questionPackage must not be null");
        List<PendingRun> pendingRuns = engines.stream()
                .sorted(Comparator.comparing(engine -> engine.metadata().engineName().name()))
                .map(engine -> submit(engine, questionPackage))
                .toList();

        return pendingRuns.stream()
                .map(this::await)
                .toList();
    }

    public OcrEngineResult extractWithEngine(OcrEngineName engineName, QuestionPackage questionPackage) {
        Objects.requireNonNull(questionPackage, "questionPackage must not be null");
        OcrEngine engine = findEngine(engineName);
        return runEngine(
                engine, questionPackage, 1, null,
                runIdSupplier.get(), Instant.now(clock), System.nanoTime()
        );
    }

    public OcrEngineResult rerunWithEngine(
            OcrEngineName engineName,
            QuestionPackage questionPackage,
            OcrEngineResult previousResult
    ) {
        Objects.requireNonNull(previousResult, "previousResult must not be null");
        if (!previousResult.runMetadata().traceId().equals(questionPackage.traceId())) {
            throw new IllegalArgumentException("Retry must use the same traceId as the original run");
        }
        if (previousResult.engineName() != engineName) {
            throw new IllegalArgumentException("Retry must use the same OCR engine");
        }
        UUID originalRunId = previousResult.runMetadata().originalRunId() == null
                ? previousResult.runMetadata().runId()
                : previousResult.runMetadata().originalRunId();
        OcrEngine engine = findEngine(engineName);
        return runEngine(
                engine,
                questionPackage,
                previousResult.runMetadata().attemptNumber() + 1,
                originalRunId,
                runIdSupplier.get(),
                Instant.now(clock),
                System.nanoTime()
        );
    }

    private OcrEngine findEngine(OcrEngineName engineName) {
        return engines.stream()
                .filter(candidate -> candidate.metadata().engineName() == engineName)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing OCR engine: " + engineName));
    }

    private PendingRun submit(OcrEngine engine, QuestionPackage questionPackage) {
        UUID runId = runIdSupplier.get();
        Instant startedAt = Instant.now(clock);
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + engineTimeout.toNanos();
        Future<OcrEngineResult> future = executorService.submit(
                () -> runEngine(
                        engine,
                        questionPackage,
                        1,
                        null,
                        runId,
                        startedAt,
                        startedNanos
                )
        );
        return new PendingRun(
                engine.metadata(),
                questionPackage.traceId(),
                runId,
                startedAt,
                startedNanos,
                deadlineNanos,
                future
        );
    }

    private OcrEngineResult await(PendingRun pending) {
        try {
            if (pending.future().isDone()) {
                return pending.future().get();
            }
            long remainingNanos = pending.deadlineNanos() - System.nanoTime();
            if (remainingNanos <= 0) {
                return timeout(pending);
            }
            return pending.future().get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            return timeout(pending);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.future().cancel(true);
            return orchestrationFailure(pending, "OCR orchestration was interrupted");
        } catch (ExecutionException e) {
            return orchestrationFailure(
                    pending,
                    "OCR orchestration failed: " + safeMessage(rootCause(e))
            );
        }
    }

    private OcrEngineResult timeout(PendingRun pending) {
        pending.future().cancel(true);
        return OcrEngineResult.failed(
                pending.metadata(),
                completedRunMetadata(pending),
                OcrEngineFailureType.TIMEOUT,
                "",
                "OCR engine exceeded timeout of " + engineTimeout
        );
    }

    private OcrEngineResult orchestrationFailure(PendingRun pending, String reason) {
        return OcrEngineResult.failed(
                pending.metadata(),
                completedRunMetadata(pending),
                OcrEngineFailureType.UNKNOWN,
                "",
                reason
        );
    }

    private OcrRunMetadata completedRunMetadata(PendingRun pending) {
        return OcrRunMetadata.firstAttempt(
                pending.traceId(),
                pending.runId(),
                pending.startedAt(),
                Instant.now(clock),
                elapsedMillis(pending.startedNanos())
        );
    }

    private OcrEngineResult runEngine(
            OcrEngine engine,
            QuestionPackage questionPackage,
            int attemptNumber,
            UUID originalRunId,
            UUID runId,
            Instant startedAt,
            long startTime
    ) {
        OcrEngineMetadata metadata = engine.metadata();
        String rawOutput = "";

        try {
            rawOutput = engine.extractRaw(questionPackage);
            OcrBundleResult bundle = bundleParser.parse(rawOutput);
            OcrRunMetadata runMetadata = runMetadata(
                    questionPackage, runId, attemptNumber, originalRunId, startedAt, startTime
            );

            return OcrEngineResult.success(
                    metadata,
                    runMetadata,
                    bundle,
                    rawOutput,
                    null,
                    null,
                    evidenceFrom(bundle)
            );
        } catch (OcrNotApplicableException e) {
            return OcrEngineResult.notApplicable(
                    metadata,
                    runMetadata(questionPackage, runId, attemptNumber, originalRunId, startedAt, startTime),
                    extractFailureMessage(e)
            );
        } catch (Exception e) {
            return OcrEngineResult.failed(
                    metadata,
                    runMetadata(questionPackage, runId, attemptNumber, originalRunId, startedAt, startTime),
                    detectFailureType(e),
                    rawOutput,
                    extractFailureMessage(e)
            );
        }
    }

    private OcrRunMetadata runMetadata(
            QuestionPackage questionPackage,
            UUID runId,
            int attemptNumber,
            UUID originalRunId,
            Instant startedAt,
            long startTime
    ) {
        long durationMs = elapsedMillis(startTime);
        return new OcrRunMetadata(
                questionPackage.traceId(),
                runId,
                attemptNumber,
                attemptNumber > 1,
                originalRunId,
                startedAt,
                Instant.now(clock),
                durationMs
        );
    }

    private List<OcrEvidence> evidenceFrom(OcrBundleResult bundle) {
        return List.of(
                readingEvidence(OcrEvidenceType.PRIMARY_READING, "Primary OCR reading", bundle.primary()),
                readingEvidence(OcrEvidenceType.VERIFICATION_READING, "Independent verification reading", bundle.verification()),
                readingEvidence(OcrEvidenceType.THRESHOLD_READING, "Threshold image reading", bundle.thresholdRead())
        );
    }

    private OcrEvidence readingEvidence(
            OcrEvidenceType type,
            String description,
            OcrReading reading
    ) {
        return new OcrEvidence(
                type,
                description,
                Map.of(
                        "rawText", reading.rawText() == null ? "" : reading.rawText(),
                        "clearlyReadable", Boolean.toString(reading.clearlyReadable())
                )
        );
    }

    private long elapsedMillis(long startTime) {
        return Math.max(0L, (System.nanoTime() - startTime) / 1_000_000L);
    }

    private Duration requirePositive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private record PendingRun(
            OcrEngineMetadata metadata,
            UUID traceId,
            UUID runId,
            Instant startedAt,
            long startedNanos,
            long deadlineNanos,
            Future<OcrEngineResult> future
    ) {
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
