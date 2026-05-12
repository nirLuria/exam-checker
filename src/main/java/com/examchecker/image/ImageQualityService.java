package com.examchecker.image;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ImageQualityService {

    private static final int MIN_WIDTH = 250;
    private static final int MIN_HEIGHT = 120;

    private static final double BLUR_THRESHOLD = 80.0;
    private static final double LOW_CONTRAST_THRESHOLD = 35.0;
    private static final double TOO_DARK_THRESHOLD = 45.0;
    private static final double TOO_BRIGHT_THRESHOLD = 220.0;

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

        boolean tooSmall = width < MIN_WIDTH || height < MIN_HEIGHT;

        double brightness = calculateAverageBrightness(image);
        double contrast = calculateContrast(image);
        double blurScore = calculateBlurScore(image);

        boolean blurry = blurScore < BLUR_THRESHOLD;
        boolean lowContrast = contrast < LOW_CONTRAST_THRESHOLD;
        boolean tooDark = brightness < TOO_DARK_THRESHOLD;
        boolean tooBright = brightness > TOO_BRIGHT_THRESHOLD;

        StringBuilder reason = new StringBuilder();

        if (tooSmall) {
            reason.append("Image is too small. ");
        }

        if (blurry) {
            reason.append("Image appears blurry. ");
        }

        if (lowContrast) {
            reason.append("Image has low contrast. ");
        }

        if (tooDark) {
            reason.append("Image is too dark. ");
        }

        if (tooBright) {
            reason.append("Image is too bright. ");
        }

        boolean suspicious =
                tooSmall ||
                        blurry ||
                        lowContrast ||
                        tooDark ||
                        tooBright;

        return new ImageQualityReport(
                tooSmall,
                blurry,
                lowContrast,
                tooDark,
                tooBright,
                suspicious,
                reason.toString().trim(),
                blurScore,
                contrast,
                brightness
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
                blurScore,
                contrastScore,
                brightnessScore
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