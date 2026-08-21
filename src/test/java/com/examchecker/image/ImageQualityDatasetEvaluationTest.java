package com.examchecker.image;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageQualityDatasetEvaluationTest {

    private static final Path DATASET_ROOT = Path.of("src", "test", "resources");
    private static final Path REPORT_PATH = Path.of(
            "target",
            "image-quality",
            "dataset-report.csv"
    );

    private final ImageQualityService service = new ImageQualityService(new ImageQualityPolicy());

    @Test
    void evaluatesAllRealDatasetImagesWithoutOcr() throws IOException {
        List<Path> imageFiles = findImageFiles();
        List<String> reportLines = new ArrayList<>();
        Map<ImageQualityDecision, Integer> decisionCounts = new EnumMap<>(ImageQualityDecision.class);

        reportLines.add(
                "file,width,height,blur,contrast,brightness,qualityScore,decision,reason,policyVersion"
        );

        for (Path imageFile : imageFiles) {
            ImageQualityReport report = service.analyze(imageFile.toFile());

            assertTrue(report.analyzable(), () -> "Could not analyze " + imageFile);
            assertFalse(Double.isNaN(report.blurScore()), () -> "NaN blur score for " + imageFile);
            assertFalse(Double.isInfinite(report.blurScore()), () -> "Infinite blur score for " + imageFile);
            assertFalse(Double.isNaN(report.contrastScore()), () -> "NaN contrast score for " + imageFile);
            assertFalse(Double.isInfinite(report.contrastScore()), () -> "Infinite contrast score for " + imageFile);
            assertFalse(Double.isNaN(report.brightnessScore()), () -> "NaN brightness score for " + imageFile);
            assertFalse(Double.isInfinite(report.brightnessScore()), () -> "Infinite brightness score for " + imageFile);
            assertTrue(report.qualityScore() >= 0 && report.qualityScore() <= 100);
            assertEquals("image-quality-v2", report.policyVersion());

            decisionCounts.merge(report.decision(), 1, Integer::sum);
            reportLines.add(toCsvLine(imageFile, report));
        }

        assertEquals(35, imageFiles.size(), "Dataset image count changed; review the new baseline");
        assertEquals(27, decisionCounts.getOrDefault(ImageQualityDecision.ACCEPT, 0));
        assertEquals(8, decisionCounts.getOrDefault(ImageQualityDecision.REVIEW, 0));
        assertEquals(0, decisionCounts.getOrDefault(ImageQualityDecision.REJECT, 0));

        Files.createDirectories(REPORT_PATH.getParent());
        Files.write(REPORT_PATH, reportLines, StandardCharsets.UTF_8);

        System.out.printf(
                "Image quality dataset: total=%d, accept=%d, review=%d, reject=%d, report=%s%n",
                imageFiles.size(),
                decisionCounts.getOrDefault(ImageQualityDecision.ACCEPT, 0),
                decisionCounts.getOrDefault(ImageQualityDecision.REVIEW, 0),
                decisionCounts.getOrDefault(ImageQualityDecision.REJECT, 0),
                REPORT_PATH
        );
    }

    private List<Path> findImageFiles() throws IOException {
        try (var files = Files.walk(DATASET_ROOT)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isImage)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private boolean isImage(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".png")
                || fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg");
    }

    private String toCsvLine(Path imageFile, ImageQualityReport report) {
        return String.join(",",
                csv(DATASET_ROOT.relativize(imageFile).toString().replace('\\', '/')),
                Integer.toString(report.width()),
                Integer.toString(report.height()),
                format(report.blurScore()),
                format(report.contrastScore()),
                format(report.brightnessScore()),
                format(report.qualityScore()),
                report.decision().name(),
                csv(report.reason()),
                report.policyVersion()
        );
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
