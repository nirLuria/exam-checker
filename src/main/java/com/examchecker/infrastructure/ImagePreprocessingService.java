package com.examchecker.infrastructure;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import jakarta.annotation.PreDestroy;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

@Service
public class ImagePreprocessingService {

    private final Path sessionDir;

    public ImagePreprocessingService() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        this.sessionDir = Paths.get("debug-images", "session-" + timestamp);

        try {
            Files.createDirectories(sessionDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create debug image folder", e);
        }
    }

    public ProcessedImage preprocess(byte[] originalBytes)
    {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));

            if (original == null) {
                throw new IllegalArgumentException("Could not read image");
            }

            save(original, "01-original.png");

            BufferedImage resized = resizeIfNeeded(original);
            save(resized, "02-resized.png");

            BufferedImage grayscale = toGrayscale(resized);
            save(grayscale, "03-grayscale.png");

            BufferedImage contrast = increaseContrast(grayscale, 1.35);
            save(contrast, "04-contrast.png");

            BufferedImage sharpened = sharpen(contrast);
            save(sharpened, "05-sharpened.png");


            //for debugging
            BufferedImage threshold = threshold(sharpened);
            save(threshold, "06-threshold.png");

            return new ProcessedImage(
                    toBytes(sharpened),
                    toBytes(threshold)
            );
        } catch (IOException e) {
            throw new RuntimeException("Image preprocessing failed", e);
        }
    }

    public record ProcessedImage(
            byte[] sharpenedImage,
            byte[] thresholdImage
    ) {
    }


    private BufferedImage resizeIfNeeded(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int minWidth = 1200;
        int maxWidth = 2200;

        if (width >= minWidth && width <= maxWidth) {
            return image;
        }

        double scale;

        if (width < minWidth) {
            scale = (double) minWidth / width;
        } else {
            scale = (double) maxWidth / width;
        }

        int newWidth = (int) Math.round(width * scale);
        int newHeight = (int) Math.round(height * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    private BufferedImage toGrayscale(BufferedImage image) {
        BufferedImage gray = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D g = gray.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return gray;
    }

    private BufferedImage increaseContrast(BufferedImage image, double factor) {
        BufferedImage result = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int gray = rgb & 0xFF;

                int adjusted = (int) ((gray - 128) * factor + 128);
                adjusted = Math.max(0, Math.min(255, adjusted));

                int newRgb = new Color(adjusted, adjusted, adjusted).getRGB();
                result.setRGB(x, y, newRgb);
            }
        }

        return result;
    }

    private BufferedImage sharpen(BufferedImage image) {
        float[] kernel = {
                0f, -0.4f, 0f,
                -0.4f, 2.6f, -0.4f,
                0f, -0.4f, 0f
        };

        java.awt.image.Kernel sharpeningKernel =
                new java.awt.image.Kernel(3, 3, kernel);

        java.awt.image.ConvolveOp op =
                new java.awt.image.ConvolveOp(
                        sharpeningKernel,
                        java.awt.image.ConvolveOp.EDGE_NO_OP,
                        null
                );

        return op.filter(image, null);
    }

    private void save(BufferedImage image, String filename) throws IOException {
        Path output = sessionDir.resolve(filename);
        ImageIO.write(image, "png", output.toFile());
    }

    private byte[] toBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    @PreDestroy
    public void cleanupOnShutdown() {
        try {
            if (!Files.exists(sessionDir)) {
                return;
            }

            Files.walk(sessionDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

            Path parent = sessionDir.getParent();

            if (parent != null && Files.exists(parent) && isDirectoryEmpty(parent)) {
                Files.delete(parent);
            }

        } catch (IOException e) {
            System.err.println("Failed to clean debug images: " + e.getMessage());
        }
    }

    private boolean isDirectoryEmpty(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        }
    }

    private BufferedImage threshold(BufferedImage image) {

        BufferedImage result = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_BINARY
        );

        Graphics2D g = result.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return result;
    }
}