package com.examchecker.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class LocalRejectedQuestionImageArchive implements RejectedQuestionImageArchive {

    private final Path root;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public LocalRejectedQuestionImageArchive(
            @Value("${exam-checker.rejected-images.root:runtime-data/rejected-question-images}") String root,
            ObjectMapper objectMapper
    ) {
        this(Path.of(root), objectMapper, Clock.systemUTC());
    }

    LocalRejectedQuestionImageArchive(Path root, ObjectMapper objectMapper, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ArchiveResult archive(MultipartFile file, ImageQualityReport report) {
        if (!report.suspicious()) {
            return ArchiveResult.notRequired();
        }

        try {
            byte[] content = file.getBytes();
            String archiveId = UUID.randomUUID().toString();
            LocalDate date = LocalDate.now(clock);
            Path relativeDirectory = Path.of(
                    Integer.toString(date.getYear()),
                    String.format(Locale.ROOT, "%02d", date.getMonthValue()),
                    String.format(Locale.ROOT, "%02d", date.getDayOfMonth()),
                    report.decision().name().toLowerCase(Locale.ROOT)
            );
            Path directory = root.resolve(relativeDirectory).normalize();
            if (!directory.startsWith(root)) {
                return ArchiveResult.failed("Resolved archive path is outside configured root");
            }

            Files.createDirectories(directory);
            String extension = safeExtension(file.getOriginalFilename());
            Path imagePath = directory.resolve(archiveId + extension);
            Path metadataPath = directory.resolve(archiveId + ".json");
            Files.write(imagePath, content);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), Map.ofEntries(
                    Map.entry("archiveId", archiveId),
                    Map.entry("capturedAt", Instant.now(clock).toString()),
                    Map.entry("sha256", sha256(content)),
                    Map.entry("contentType", safe(file.getContentType())),
                    Map.entry("sizeBytes", content.length),
                    Map.entry("decision", report.decision()),
                    Map.entry("qualityScore", report.qualityScore()),
                    Map.entry("reasonCodes", report.reasonCodes()),
                    Map.entry("reason", report.reason()),
                    Map.entry("policyVersion", report.policyVersion()),
                    Map.entry("width", report.width()),
                    Map.entry("height", report.height()),
                    Map.entry("blurScore", report.blurScore()),
                    Map.entry("contrastScore", report.contrastScore()),
                    Map.entry("brightnessScore", report.brightnessScore())
            ));
            return ArchiveResult.stored(archiveId, root.relativize(imagePath).toString().replace('\\', '/'));
        } catch (IOException | RuntimeException e) {
            return ArchiveResult.failed(e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String safeExtension(String originalFilename) {
        if (originalFilename == null) {
            return ".bin";
        }
        String lower = originalFilename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
        if (lower.endsWith(".webp")) return ".webp";
        return ".bin";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
