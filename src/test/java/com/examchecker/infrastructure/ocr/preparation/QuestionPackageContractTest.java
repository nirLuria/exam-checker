package com.examchecker.infrastructure.ocr.preparation;

import com.examchecker.image.ImageQualityDecision;
import com.examchecker.image.ImageQualityReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionPackageContractTest {

    private static final UUID TRACE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant CREATED_AT = Instant.parse("2026-08-23T08:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createsVersionedPackageFromExactCropBytes() throws Exception {
        byte[] cropBytes = new byte[]{1, 2, 3, 4};
        QuestionPackageFactory factory = factory();

        QuestionPackage questionPackage = factory.create(
                new MockMultipartFile("file", "question.png", "image/png", cropBytes),
                reference(),
                context(),
                qualityReport()
        );

        assertEquals(QuestionPackage.CURRENT_CONTRACT_VERSION, questionPackage.contractVersion());
        assertEquals(TRACE_ID, questionPackage.traceId());
        assertEquals(CREATED_AT, questionPackage.createdAt());
        assertArrayEquals(cropBytes, questionPackage.image().content());
        assertEquals("image/png", questionPackage.image().contentType());
        assertEquals(64, questionPackage.image().sha256().length());
        assertEquals(300, questionPackage.image().width());
        assertEquals(ImageQualityDecision.PASS, questionPackage.imageQuality().decision());
    }

    @Test
    void imageContentIsDefensivelyCopied() {
        byte[] source = new byte[]{10, 20, 30};
        QuestionImage image = QuestionImage.create(source, "image/png", 300, 150);
        String originalHash = image.sha256();

        source[0] = 99;
        byte[] returned = image.content();
        returned[1] = 88;

        assertArrayEquals(new byte[]{10, 20, 30}, image.content());
        assertEquals(originalHash, image.sha256());
    }

    @Test
    void jsonRoundTripPreservesContractAndExcludesValidationInformation() throws Exception {
        QuestionPackage original = factory().create(
                new MockMultipartFile("file", "question.png", "image/png", new byte[]{1, 2, 3}),
                reference(),
                context(),
                qualityReport()
        );

        String json = objectMapper.writeValueAsString(original);
        QuestionPackage restored = objectMapper.readValue(json, QuestionPackage.class);
        JsonNode tree = objectMapper.readTree(json);

        assertEquals(original, restored);
        assertFalse(tree.has("answerKey"));
        assertFalse(tree.has("expectedAnswer"));
        assertFalse(tree.has("correct"));
        assertFalse(tree.has("score"));
        assertFalse(tree.get("ocrContext").has("answerKey"));
    }

    @Test
    void rejectsTamperedImageHashDuringDeserialization() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuestionImage(new byte[]{1, 2, 3}, "image/png", "bad-hash", 300, 150)
        );
    }

    @Test
    void rejectsUnsupportedContractVersion() {
        QuestionImage image = QuestionImage.create(new byte[]{1}, "image/png", 300, 150);
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuestionPackage(
                        "question-package-v999",
                        TRACE_ID,
                        CREATED_AT,
                        reference(),
                        image,
                        QuestionImageQuality.from(qualityReport()),
                        context()
                )
        );
    }

    private QuestionPackageFactory factory() {
        return new QuestionPackageFactory(
                Clock.fixed(CREATED_AT, ZoneOffset.UTC),
                () -> TRACE_ID
        );
    }

    private QuestionReference reference() {
        return new QuestionReference("exam-123", 2, "4", "b");
    }

    private OcrContext context() {
        return new OcrContext("Solve the exercise", QuestionType.ARITHMETIC, List.of("cm"));
    }

    private ImageQualityReport qualityReport() {
        return new ImageQualityReport(
                false, false, false, false, false, false,
                "", List.of(),
                100, 50, 128, true, 300, 150, 100,
                ImageQualityDecision.PASS,
                "image-quality-v2"
        );
    }
}
