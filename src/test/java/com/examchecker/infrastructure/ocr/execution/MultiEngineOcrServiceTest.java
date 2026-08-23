package com.examchecker.infrastructure.ocr.execution;

import com.examchecker.infrastructure.ocr.contract.*;
import com.examchecker.infrastructure.ocr.postprocessing.OcrBundleParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.examchecker.image.ImageQualityDecision;
import com.examchecker.infrastructure.ocr.preparation.OcrContext;
import com.examchecker.infrastructure.ocr.preparation.QuestionImage;
import com.examchecker.infrastructure.ocr.preparation.QuestionImageQuality;
import com.examchecker.infrastructure.ocr.preparation.QuestionPackage;
import com.examchecker.infrastructure.ocr.preparation.QuestionReference;
import com.examchecker.infrastructure.ocr.preparation.QuestionType;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiEngineOcrServiceTest {

    private final OcrBundleParser parser = new OcrBundleParser(new ObjectMapper());
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @AfterEach
    void shutdownExecutor() {
        executorService.shutdownNow();
    }

    @Test
    void runsEachEngineOnceWithSameInputInStableOrder() {
        FakeEngine openAi = FakeEngine.success(OcrEngineName.OPENAI);
        FakeEngine gemini = FakeEngine.success(OcrEngineName.GEMINI);
        MultiEngineOcrService service = service(List.of(openAi, gemini));
        QuestionPackage input = input();

        List<OcrEngineResult> results = service.extractWithAllEngines(input);

        assertEquals(List.of(OcrEngineName.GEMINI, OcrEngineName.OPENAI),
                results.stream().map(OcrEngineResult::engineName).toList());
        assertTrue(results.stream().allMatch(OcrEngineResult::succeeded));
        assertEquals(1, openAi.callCount);
        assertEquals(1, gemini.callCount);
        assertSame(input, openAi.receivedInput);
        assertSame(input, gemini.receivedInput);
        assertTrue(results.stream().allMatch(result -> result.runMetadata().traceId().equals(input.traceId())));
        assertTrue(results.stream().allMatch(result -> result.evidence().size() == 3));
    }

    @Test
    void parseFailureDoesNotStopOtherEngineAndPreservesRawOutput() {
        FakeEngine gemini = FakeEngine.raw(OcrEngineName.GEMINI, "{invalid-json}");
        FakeEngine openAi = FakeEngine.success(OcrEngineName.OPENAI);
        MultiEngineOcrService service = service(List.of(openAi, gemini));

        List<OcrEngineResult> results = service.extractWithAllEngines(input());

        OcrEngineResult failed = results.get(0);
        assertTrue(failed.failed());
        assertEquals(OcrEngineFailureType.PARSE_ERROR, failed.failureType());
        assertEquals("{invalid-json}", failed.rawOutput());
        assertTrue(results.get(1).succeeded());
    }

    @Test
    void classifiesTimeoutWithoutStoppingOtherEngine() {
        FakeEngine gemini = FakeEngine.failure(OcrEngineName.GEMINI, new RuntimeException("request timed out"));
        FakeEngine openAi = FakeEngine.success(OcrEngineName.OPENAI);
        MultiEngineOcrService service = service(List.of(gemini, openAi));

        List<OcrEngineResult> results = service.extractWithAllEngines(input());

        assertEquals(OcrEngineFailureType.TIMEOUT, results.get(0).failureType());
        assertTrue(results.get(1).succeeded());
    }

    @Test
    void rejectsDuplicateEngineNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service(
                        List.of(
                                FakeEngine.success(OcrEngineName.GEMINI),
                                FakeEngine.success(OcrEngineName.GEMINI)
                        )
                )
        );
    }

    @Test
    void reportsMissingRequestedEngineExplicitly() {
        MultiEngineOcrService service = service(
                List.of(FakeEngine.success(OcrEngineName.GEMINI))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.extractWithEngine(OcrEngineName.OPENAI, input())
        );

        assertTrue(exception.getMessage().contains("OPENAI"));
    }

    @Test
    void marksUnsupportedQuestionTypeAsNotApplicableWithoutEngineFailure() {
        FakeEngine gemini = FakeEngine.failure(
                OcrEngineName.GEMINI,
                new OcrNotApplicableException("Question type is unsupported")
        );
        MultiEngineOcrService service = service(List.of(gemini));

        OcrEngineResult result = service.extractWithEngine(OcrEngineName.GEMINI, input());

        assertEquals(OcrEngineStatus.NOT_APPLICABLE, result.status());
        assertTrue(result.notApplicable());
        assertTrue(!result.failed());
        assertEquals("Question type is unsupported", result.failureReason());
    }

    @Test
    void rerunKeepsTraceButIsNotCountedAsIndependentSource() {
        FakeEngine gemini = FakeEngine.success(OcrEngineName.GEMINI);
        MultiEngineOcrService service = service(List.of(gemini));
        QuestionPackage input = input();

        OcrEngineResult first = service.extractWithEngine(OcrEngineName.GEMINI, input);
        OcrEngineResult retry = service.rerunWithEngine(OcrEngineName.GEMINI, input, first);

        assertEquals(input.traceId(), retry.runMetadata().traceId());
        assertEquals(2, retry.runMetadata().attemptNumber());
        assertTrue(retry.runMetadata().retry());
        assertEquals(first.runMetadata().runId(), retry.runMetadata().originalRunId());
        assertTrue(!first.runMetadata().runId().equals(retry.runMetadata().runId()));
        assertEquals(2, gemini.callCount);
    }

    @Test
    void startsIndependentEnginesConcurrently() {
        CountDownLatch allStarted = new CountDownLatch(2);
        MultiEngineOcrService service = service(List.of(
                coordinatedEngine(OcrEngineName.GEMINI, allStarted),
                coordinatedEngine(OcrEngineName.QWEN, allStarted)
        ));

        List<OcrEngineResult> results = service.extractWithAllEngines(input());

        assertTrue(results.stream().allMatch(OcrEngineResult::succeeded));
        assertEquals(0, allStarted.getCount());
    }

    @Test
    void oneEngineTimeoutDoesNotDiscardAnotherEngineResult() {
        MultiEngineOcrService service = service(
                List.of(
                        sleepingEngine(OcrEngineName.GEMINI, 500),
                        FakeEngine.success(OcrEngineName.QWEN)
                ),
                Duration.ofMillis(40)
        );

        List<OcrEngineResult> results = service.extractWithAllEngines(input());

        assertEquals(OcrEngineStatus.TIMEOUT, results.get(0).status());
        assertEquals(OcrEngineName.GEMINI, results.get(0).engineName());
        assertTrue(results.get(1).succeeded());
        assertEquals(OcrEngineName.QWEN, results.get(1).engineName());
    }

    private QuestionPackage input() {
        return new QuestionPackage(
                QuestionPackage.CURRENT_CONTRACT_VERSION,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                Instant.parse("2026-08-23T08:00:00Z"),
                new QuestionReference("exam-1", 1, "1", ""),
                QuestionImage.create(new byte[]{1, 2, 3}, "image/png", 300, 150),
                new QuestionImageQuality(100, ImageQualityDecision.PASS, List.of(), "image-quality-v2"),
                new OcrContext("", QuestionType.ARITHMETIC, List.of())
        );
    }

    private MultiEngineOcrService service(List<OcrEngine> engines) {
        return service(engines, Duration.ofSeconds(1));
    }

    private MultiEngineOcrService service(List<OcrEngine> engines, Duration timeout) {
        return new MultiEngineOcrService(
                engines,
                parser,
                executorService,
                timeout,
                Clock.systemUTC(),
                UUID::randomUUID
        );
    }

    private OcrEngine coordinatedEngine(OcrEngineName name, CountDownLatch allStarted) {
        return new OcrEngine() {
            @Override
            public OcrEngineMetadata metadata() {
                return new OcrEngineMetadata(name, "test-model", "test-adapter-v1");
            }

            @Override
            public String extractRaw(QuestionPackage questionPackage) {
                allStarted.countDown();
                try {
                    if (!allStarted.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("Engines did not start concurrently");
                    }
                    return validRawOutput();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            }
        };
    }

    private OcrEngine sleepingEngine(OcrEngineName name, long delayMs) {
        return new OcrEngine() {
            @Override
            public OcrEngineMetadata metadata() {
                return new OcrEngineMetadata(name, "test-model", "test-adapter-v1");
            }

            @Override
            public String extractRaw(QuestionPackage questionPackage) {
                try {
                    Thread.sleep(delayMs);
                    return validRawOutput();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            }
        };
    }

    private static String validRawOutput() {
        return """
                {
                  "primary": {"rawText": "5+3=8", "isClearlyReadable": true},
                  "verification": {"rawText": "5+3=8", "isClearlyReadable": true},
                  "thresholdRead": {"rawText": "5+3=8", "isClearlyReadable": true},
                  "suspiciousCheck": {
                    "suspicious": false,
                    "reason": "",
                    "suggestedRawText": ""
                  }
                }
                """;
    }

    private static final class FakeEngine implements OcrEngine {

        private final OcrEngineMetadata metadata;
        private final String rawOutput;
        private final RuntimeException failure;
        private int callCount;
        private QuestionPackage receivedInput;

        private FakeEngine(OcrEngineName name, String rawOutput, RuntimeException failure) {
            this.metadata = new OcrEngineMetadata(name, "test-model", "test-adapter-v1");
            this.rawOutput = rawOutput;
            this.failure = failure;
        }

        static FakeEngine success(OcrEngineName name) {
            return raw(name, validRawOutput());
        }

        static FakeEngine raw(OcrEngineName name, String rawOutput) {
            return new FakeEngine(name, rawOutput, null);
        }

        static FakeEngine failure(OcrEngineName name, RuntimeException failure) {
            return new FakeEngine(name, "", failure);
        }

        @Override
        public OcrEngineMetadata metadata() {
            return metadata;
        }

        @Override
        public String extractRaw(QuestionPackage file) {
            callCount++;
            receivedInput = file;
            if (failure != null) {
                throw failure;
            }
            return rawOutput;
        }
    }
}
