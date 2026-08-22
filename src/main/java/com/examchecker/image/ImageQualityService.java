package com.examchecker.image;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageQualityService {

    private final ImageQualityPolicy policy;

    public ImageQualityService(ImageQualityPolicy policy) {
        this.policy = policy;
    }

    public ImageQualityReport analyze(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) {
                return suspicious("Could not read image file", 0, 0, 0);
            }

            return analyze(image);

        } catch (IOException e) {
            return suspicious("Failed to analyze image: " + e.getMessage(), 0, 0, 0);
        }
    }

    public ImageQualityReport analyze(File imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile);

            if (image == null) {
                return suspicious("Could not read image file", 0, 0, 0);
            }

            return analyze(image);

        } catch (IOException e) {
            return suspicious("Failed to analyze image: " + e.getMessage(), 0, 0, 0);
        }
    }

    private ImageQualityReport analyze(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        boolean tooSmall = policy.isTooSmall(width, height);

        double brightness = calculateAverageBrightness(image);
        double contrast = calculateContrast(image);
        double blurScore = calculateBlurScore(image);

        boolean blurry = policy.isBlurry(blurScore);
        boolean lowContrast = policy.isLowContrast(contrast);
        boolean tooDark = policy.isTooDark(brightness);
        boolean tooBright = policy.isTooBright(brightness);

        StringBuilder reason = new StringBuilder();
        List<ImageQualityReasonCode> reasonCodes = new ArrayList<>();

        if (tooSmall) {
            reason.append("Image is too small. ");
            reasonCodes.add(ImageQualityReasonCode.TOO_SMALL);
        }

        if (blurry) {
            reason.append("Image appears blurry. ");
            reasonCodes.add(ImageQualityReasonCode.BLURRY);
        }

        if (lowContrast) {
            reason.append("Image has low contrast. ");
            reasonCodes.add(ImageQualityReasonCode.LOW_CONTRAST);
        }

        if (tooDark) {
            reason.append("Image is too dark. ");
            reasonCodes.add(ImageQualityReasonCode.TOO_DARK);
        }

        if (tooBright) {
            reason.append("Image is too bright. ");
            reasonCodes.add(ImageQualityReasonCode.TOO_BRIGHT);
        }

        boolean suspicious =
                tooSmall ||
                        blurry ||
                        lowContrast ||
                        tooDark ||
                        tooBright;

        double qualityScore = policy.calculateScore(
                tooSmall,
                blurry,
                lowContrast,
                tooDark,
                tooBright
        );

        return new ImageQualityReport(
                tooSmall,
                blurry,
                lowContrast,
                tooDark,
                tooBright,
                suspicious,
                reason.toString().trim(),
                reasonCodes,
                blurScore,
                contrast,
                brightness,
                true,
                width,
                height,
                qualityScore,
                policy.decide(qualityScore),
                policy.version()
        );
    }

    private ImageQualityReport suspicious(
            String reason,
            double blurScore,
            double contrastScore,
            double brightnessScore
    ) {
        return new ImageQualityReport(
                false,
                false,
                false,
                false,
                false,
                true,
                reason,
                List.of(ImageQualityReasonCode.UNREADABLE_FILE),
                blurScore,
                contrastScore,
                brightnessScore,
                false,
                0,
                0,
                0,
                ImageQualityDecision.RETRY_CAPTURE,
                policy.version()
        );
    }

    private double calculateAverageBrightness(BufferedImage image) {
        double sum = 0;
        int count = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int gray = getGray(image, x, y);
                sum += gray;
                count++;
            }
        }

        return sum / count;
    }

    private double calculateContrast(BufferedImage image) {
        double mean = calculateAverageBrightness(image);
        double sumSquaredDiff = 0;
        int count = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int gray = getGray(image, x, y);
                double diff = gray - mean;
                sumSquaredDiff += diff * diff;
                count++;
            }
        }

        return Math.sqrt(sumSquaredDiff / count);
    }

    private double calculateBlurScore(BufferedImage image) {
        double sum = 0;
        double sumSquared = 0;
        int count = 0;

        for (int y = 1; y < image.getHeight() - 1; y++) {
            for (int x = 1; x < image.getWidth() - 1; x++) {

                int center = getGray(image, x, y);
                int left = getGray(image, x - 1, y);
                int right = getGray(image, x + 1, y);
                int top = getGray(image, x, y - 1);
                int bottom = getGray(image, x, y + 1);

                int laplacian = (4 * center) - left - right - top - bottom;

                sum += laplacian;
                sumSquared += laplacian * laplacian;
                count++;
            }
        }

        if (count == 0) {
            return 0;
        }

        double mean = sum / count;
        return (sumSquared / count) - (mean * mean);
    }

    private int getGray(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);

        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;

        return (r + g + b) / 3;
    }
}
