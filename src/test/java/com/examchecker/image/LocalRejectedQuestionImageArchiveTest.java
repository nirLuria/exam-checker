package com.examchecker.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRejectedQuestionImageArchiveTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-22T12:34:56Z"),
            ZoneOffset.UTC
    );

    @Test
    void storesRejectedCropAndSearchableMetadata() throws Exception {
        LocalRejectedQuestionImageArchive archive =
                new LocalRejectedQuestionImageArchive(tempDirectory, objectMapper, clock);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "question.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        RejectedQuestionImageArchive.ArchiveResult result = archive.archive(file, rejectedReport());

        assertTrue(result.stored());
        assertTrue(result.relativeImagePath().startsWith("2026/08/22/retry_capture/"));
        Path imagePath = tempDirectory.resolve(result.relativeImagePath());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Files.readAllBytes(imagePath));

        Path metadataPath = imagePath.resolveSibling(result.archiveId() + ".json");
        JsonNode metadata = objectMapper.readTree(metadataPath.toFile());
        assertEquals(result.archiveId(), metadata.get("archiveId").asText());
        assertEquals("RETRY_CAPTURE", metadata.get("decision").asText());
        assertEquals("BLURRY", metadata.get("reasonCodes").get(0).asText());
        assertEquals("image-quality-v2", metadata.get("policyVersion").asText());
        assertEquals(64, metadata.get("sha256").asText().length());
    }

    @Test
    void doesNotStorePassingCrop() throws Exception {
        LocalRejectedQuestionImageArchive archive =
                new LocalRejectedQuestionImageArchive(tempDirectory, objectMapper, clock);

        RejectedQuestionImageArchive.ArchiveResult result = archive.archive(
                new MockMultipartFile("file", "question.png", "image/png", new byte[]{1}),
                passingReport()
        );

        assertFalse(result.stored());
        try (var files = Files.list(tempDirectory)) {
            assertEquals(0, files.count());
        }
    }

    private ImageQualityReport rejectedReport() {
        return new ImageQualityReport(
                false, true, false, false, false, true,
                "Image appears blurry.",
                List.of(ImageQualityReasonCode.BLURRY),
                10, 50, 128, true, 300, 150, 40,
                ImageQualityDecision.RETRY_CAPTURE,
                "image-quality-v2"
        );
    }

    private ImageQualityReport passingReport() {
        return new ImageQualityReport(
                false, false, false, false, false, false,
                "", List.of(),
                100, 50, 128, true, 300, 150, 100,
                ImageQualityDecision.PASS,
                "image-quality-v2"
        );
    }
}
