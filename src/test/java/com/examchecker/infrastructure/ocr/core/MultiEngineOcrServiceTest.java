package com.examchecker.infrastructure.ocr.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiEngineOcrServiceTest {

    private final OcrBundleParser parser = new OcrBundleParser(new ObjectMapper());

    @Test
    void runsEachEngineOnceWithSameInputInStableOrder() {
        FakeEngine openAi = FakeEngine.success(OcrEngineName.OPENAI);
        FakeEngine gemini = FakeEngine.success(OcrEngineName.GEMINI);
        MultiEngineOcrService service = new MultiEngineOcrService(List.of(openAi, gemini), parser);
        MockMultipartFile input = input();

        List<OcrEngineResult> results = service.extractWithAllEngines(input);

        assertEquals(List.of(OcrEngineName.GEMINI, OcrEngineName.OPENAI),
                results.stream().map(OcrEngineResult::engineName).toList());
        assertTrue(results.stream().allMatch(OcrEngineResult::succeeded));
        assertEquals(1, openAi.callCount);
        assertEquals(1, gemini.callCount);
        assertSame(input, openAi.receivedInput);
        assertSame(input, gemini.receivedInput);
    }

    @Test
    void parseFailureDoesNotStopOtherEngineAndPreservesRawOutput() {
        FakeEngine gemini = FakeEngine.raw(OcrEngineName.GEMINI, "{invalid-json}");
        FakeEngine openAi = FakeEngine.success(OcrEngineName.OPENAI);
        MultiEngineOcrService service = new MultiEngineOcrService(List.of(openAi, gemini), parser);

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
        MultiEngineOcrService service = new MultiEngineOcrService(List.of(gemini, openAi), parser);

        List<OcrEngineResult> results = service.extractWithAllEngines(input());

        assertEquals(OcrEngineFailureType.TIMEOUT, results.get(0).failureType());
        assertTrue(results.get(1).succeeded());
    }

    @Test
    void rejectsDuplicateEngineNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MultiEngineOcrService(
                        List.of(
                                FakeEngine.success(OcrEngineName.GEMINI),
                                FakeEngine.success(OcrEngineName.GEMINI)
                        ),
                        parser
                )
        );
    }

    @Test
    void reportsMissingRequestedEngineExplicitly() {
        MultiEngineOcrService service = new MultiEngineOcrService(
                List.of(FakeEngine.success(OcrEngineName.GEMINI)),
                parser
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.extractWithEngine(OcrEngineName.OPENAI, input())
        );

        assertTrue(exception.getMessage().contains("OPENAI"));
    }

    private MockMultipartFile input() {
        return new MockMultipartFile("file", "question.png", "image/png", new byte[]{1, 2, 3});
    }

    private static final class FakeEngine implements OcrEngine {

        private final OcrEngineMetadata metadata;
        private final String rawOutput;
        private final RuntimeException failure;
        private int callCount;
        private MultipartFile receivedInput;

        private FakeEngine(OcrEngineName name, String rawOutput, RuntimeException failure) {
            this.metadata = new OcrEngineMetadata(name, "test-model", "test-adapter-v1");
            this.rawOutput = rawOutput;
            this.failure = failure;
        }

        static FakeEngine success(OcrEngineName name) {
            return raw(name, OcrBundleParserTest.validRawOutput());
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
        public String extractRaw(MultipartFile file) {
            callCount++;
            receivedInput = file;
            if (failure != null) {
                throw failure;
            }
            return rawOutput;
        }
    }
}
