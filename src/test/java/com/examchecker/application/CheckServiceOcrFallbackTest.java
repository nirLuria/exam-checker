package com.examchecker.application;

import com.examchecker.image.ImageQualityDecision;
import com.examchecker.image.ImageQualityReport;
import com.examchecker.image.ImageQualityService;
import com.examchecker.image.RejectedQuestionImageArchive;
import com.examchecker.infrastructure.OpenAiClient;
import com.examchecker.infrastructure.ocr.core.MultiEngineOcrService;
import com.examchecker.infrastructure.ocr.core.OcrBundleResult;
import com.examchecker.infrastructure.ocr.core.OcrConsensusService;
import com.examchecker.infrastructure.ocr.core.OcrEngineFailureType;
import com.examchecker.infrastructure.ocr.core.OcrEngineMetadata;
import com.examchecker.infrastructure.ocr.core.OcrEngineName;
import com.examchecker.infrastructure.ocr.core.OcrEngineResult;
import com.examchecker.infrastructure.ocr.core.OcrReading;
import com.examchecker.infrastructure.ocr.core.OcrResultComparisonService;
import com.examchecker.infrastructure.ocr.core.SuspiciousCheckResult;
import com.examchecker.infrastructure.ocr.core.OcrRunMetadata;
import com.examchecker.service.CanonicalMathNormalizer;
import com.examchecker.service.MathTextNormalizer;
import com.examchecker.question.QuestionPackage;
import com.examchecker.question.QuestionPackageFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class CheckServiceOcrFallbackTest {

    @Test
    void skipsAllOcrWhenImageRequiresRecapture() {
        MultiEngineOcrService multiEngineOcrService = mock(MultiEngineOcrService.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        ImageQualityService imageQualityService = mock(ImageQualityService.class);
        RejectedQuestionImageArchive archive = mock(RejectedQuestionImageArchive.class);
        QuestionPackageFactory questionPackageFactory = new QuestionPackageFactory();
        OcrConsensusService consensusService = new OcrConsensusService(
                new OcrResultComparisonService(new CanonicalMathNormalizer())
        );
        CheckService checkService = new CheckService(
                multiEngineOcrService,
                openAiClient,
                new MathTextNormalizer(),
                imageQualityService,
                archive,
                questionPackageFactory,
                consensusService
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.png", "image/png", new byte[]{1, 2, 3}
        );
        ImageQualityReport rejected = rejectedImageQuality();
        RejectedQuestionImageArchive.ArchiveResult stored =
                RejectedQuestionImageArchive.ArchiveResult.stored("archive-1", "2026/08/22/retry_capture/archive-1.png");
        when(imageQualityService.analyze(file)).thenReturn(rejected);
        when(archive.archive(file, rejected)).thenReturn(stored);

        Map<String, Object> result = checkService.check(file);

        assertEquals("RETRY_CAPTURE", result.get("processingStatus"));
        assertEquals(true, result.get("ocrSkipped"));
        assertEquals(stored, result.get("rejectedImageArchive"));
        verifyNoInteractions(multiEngineOcrService, openAiClient);
    }

    @Test
    void triesGeminiWhenOpenAiFailsAndKeepsTeacherReview() {
        MultiEngineOcrService multiEngineOcrService = mock(MultiEngineOcrService.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        ImageQualityService imageQualityService = mock(ImageQualityService.class);
        RejectedQuestionImageArchive archive = mock(RejectedQuestionImageArchive.class);
        QuestionPackageFactory questionPackageFactory = new QuestionPackageFactory();
        OcrConsensusService consensusService = new OcrConsensusService(
                new OcrResultComparisonService(new CanonicalMathNormalizer())
        );
        CheckService checkService = new CheckService(
                multiEngineOcrService,
                openAiClient,
                new MathTextNormalizer(),
                imageQualityService,
                archive,
                questionPackageFactory,
                consensusService
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "question.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(imageQualityService.analyze(file)).thenReturn(acceptableImageQuality());
        when(archive.archive(file, acceptableImageQuality()))
                .thenReturn(RejectedQuestionImageArchive.ArchiveResult.notRequired());
        when(multiEngineOcrService.extractWithEngine(eq(OcrEngineName.OPENAI), any(QuestionPackage.class)))
                .thenReturn(failedOpenAi());
        when(multiEngineOcrService.extractWithEngine(eq(OcrEngineName.GEMINI), any(QuestionPackage.class)))
                .thenReturn(successfulGemini());
        when(openAiClient.analyzeExercise("5+3=8")).thenReturn("""
                {
                  "expression": "5+3",
                  "expected": 8,
                  "studentAnswer": 8,
                  "correct": true
                }
                """);

        Map<String, Object> result = checkService.check(file);

        assertEquals("5+3=8", result.get("rawText"));
        assertEquals(OcrEngineName.GEMINI, result.get("selectedOcrEngine"));
        assertEquals("ocr-consensus-v1", result.get("ocrConsensusPolicyVersion"));
        assertTrue(Boolean.TRUE.equals(result.get("needsTeacherReview")));
        verify(multiEngineOcrService).extractWithEngine(eq(OcrEngineName.OPENAI), any(QuestionPackage.class));
        verify(multiEngineOcrService).extractWithEngine(eq(OcrEngineName.GEMINI), any(QuestionPackage.class));
    }

    private OcrEngineResult failedOpenAi() {
        return OcrEngineResult.failed(
                metadata(OcrEngineName.OPENAI),
                runMetadata(),
                OcrEngineFailureType.TIMEOUT,
                "",
                "timed out"
        );
    }

    private OcrEngineResult successfulGemini() {
        OcrReading reading = new OcrReading("5+3=8", true);
        OcrBundleResult bundle = new OcrBundleResult(
                reading,
                reading,
                reading,
                new SuspiciousCheckResult(false, "", "")
        );
        return OcrEngineResult.success(
                metadata(OcrEngineName.GEMINI),
                runMetadata(),
                bundle,
                "raw-gemini-json",
                null,
                null,
                List.of()
        );
    }

    private OcrRunMetadata runMetadata() {
        Instant timestamp = Instant.parse("2026-08-23T08:00:00Z");
        return OcrRunMetadata.firstAttempt(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.randomUUID(),
                timestamp,
                timestamp,
                10
        );
    }

    private OcrEngineMetadata metadata(OcrEngineName engineName) {
        return new OcrEngineMetadata(engineName, "test-model", "test-adapter-v1");
    }

    private ImageQualityReport acceptableImageQuality() {
        return new ImageQualityReport(
                false,
                false,
                false,
                false,
                false,
                false,
                "",
                java.util.List.of(),
                100,
                50,
                128,
                true,
                300,
                150,
                100,
                ImageQualityDecision.PASS,
                "image-quality-v2"
        );
    }

    private ImageQualityReport rejectedImageQuality() {
        return new ImageQualityReport(
                false,
                true,
                true,
                true,
                false,
                true,
                "Image appears blurry. Image has low contrast. Image is too dark.",
                java.util.List.of(
                        com.examchecker.image.ImageQualityReasonCode.BLURRY,
                        com.examchecker.image.ImageQualityReasonCode.LOW_CONTRAST,
                        com.examchecker.image.ImageQualityReasonCode.TOO_DARK
                ),
                10,
                5,
                20,
                true,
                300,
                150,
                30,
                ImageQualityDecision.RETRY_CAPTURE,
                "image-quality-v2"
        );
    }
}
