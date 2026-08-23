package com.examchecker.question;

import com.examchecker.image.ImageQualityReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class QuestionPackageFactory {

    private final Clock clock;
    private final Supplier<UUID> traceIdSupplier;

    @Autowired
    public QuestionPackageFactory() {
        this(Clock.systemUTC(), UUID::randomUUID);
    }

    QuestionPackageFactory(Clock clock, Supplier<UUID> traceIdSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.traceIdSupplier = Objects.requireNonNull(traceIdSupplier, "traceIdSupplier must not be null");
    }

    public QuestionPackage create(
            MultipartFile crop,
            QuestionReference reference,
            OcrContext ocrContext,
            ImageQualityReport imageQualityReport
    ) throws IOException {
        Objects.requireNonNull(crop, "crop must not be null");
        Objects.requireNonNull(imageQualityReport, "imageQualityReport must not be null");
        QuestionImage image = QuestionImage.create(
                crop.getBytes(),
                crop.getContentType(),
                imageQualityReport.width(),
                imageQualityReport.height()
        );
        return QuestionPackage.create(
                traceIdSupplier.get(),
                Instant.now(clock),
                reference,
                image,
                imageQualityReport,
                ocrContext
        );
    }
}
