package com.examchecker.image;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageQualityServiceTest {

    private static final int VALID_WIDTH = 300;
    private static final int VALID_HEIGHT = 150;

    private final ImageQualityService service = new ImageQualityService(new ImageQualityPolicy());

    @Test
    void acceptsSharpHighContrastImage() throws IOException {
        BufferedImage image = checkerboard(VALID_WIDTH, VALID_HEIGHT, 10);

        ImageQualityReport report = service.analyze(asMultipartFile(image));

        assertTrue(report.analyzable());
        assertFalse(report.suspicious());
        assertEquals(ImageQualityDecision.PASS, report.decision());
        assertTrue(report.reasonCodes().isEmpty());
        assertEquals(100.0, report.qualityScore());
        assertEquals(VALID_WIDTH, report.width());
        assertEquals(VALID_HEIGHT, report.height());
        assertEquals("image-quality-v2", report.policyVersion());
    }

    @Test
    void marksSmallImageForReview() throws IOException {
        ImageQualityReport report = service.analyze(asMultipartFile(checkerboard(150, 50, 10)));

        assertTrue(report.tooSmall());
        assertEquals(70.0, report.qualityScore());
        assertEquals(ImageQualityDecision.TEACHER_REVIEW, report.decision());
        assertTrue(report.reasonCodes().contains(ImageQualityReasonCode.TOO_SMALL));
        assertTrue(report.reason().contains("too small"));
    }

    @Test
    void detectsSmoothGradientAsBlurry() throws IOException {
        ImageQualityReport report = service.analyze(asMultipartFile(horizontalGradient()));

        assertTrue(report.blurry());
        assertFalse(report.lowContrast());
        assertEquals(ImageQualityDecision.TEACHER_REVIEW, report.decision());
        assertTrue(report.reasonCodes().contains(ImageQualityReasonCode.BLURRY));
    }

    @Test
    void rejectsDarkLowContrastImage() throws IOException {
        ImageQualityReport report = service.analyze(asMultipartFile(solidImage(new Color(20, 20, 20))));

        assertTrue(report.tooDark());
        assertTrue(report.lowContrast());
        assertEquals(ImageQualityDecision.RETRY_CAPTURE, report.decision());
    }

    @Test
    void rejectsBrightLowContrastImage() throws IOException {
        ImageQualityReport report = service.analyze(asMultipartFile(solidImage(new Color(240, 240, 240))));

        assertTrue(report.tooBright());
        assertTrue(report.lowContrast());
        assertEquals(ImageQualityDecision.RETRY_CAPTURE, report.decision());
    }

    @Test
    void rejectsUnreadableInputWithStableReport() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "invalid.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        ImageQualityReport report = service.analyze(invalidFile);

        assertFalse(report.analyzable());
        assertTrue(report.suspicious());
        assertEquals(0.0, report.qualityScore());
        assertEquals(ImageQualityDecision.RETRY_CAPTURE, report.decision());
        assertEquals(
                java.util.List.of(ImageQualityReasonCode.UNREADABLE_FILE),
                report.reasonCodes()
        );
        assertEquals(0, report.width());
        assertEquals(0, report.height());
        assertEquals("Could not read image file", report.reason());
    }

    @Test
    void handlesImageTooSmallForBlurKernel() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        ImageQualityReport report = service.analyze(asMultipartFile(image));

        assertTrue(report.analyzable());
        assertEquals(0.0, report.blurScore());
        assertTrue(report.blurry());
    }

    private BufferedImage checkerboard(int width, int height, int cellSize) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean white = ((x / cellSize) + (y / cellSize)) % 2 == 0;
                image.setRGB(x, y, white ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
            }
        }
        return image;
    }

    private BufferedImage horizontalGradient() {
        BufferedImage image = new BufferedImage(VALID_WIDTH, VALID_HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < VALID_WIDTH; x++) {
            int gray = (int) Math.round((x * 255.0) / (VALID_WIDTH - 1));
            int rgb = new Color(gray, gray, gray).getRGB();
            for (int y = 0; y < VALID_HEIGHT; y++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private BufferedImage solidImage(Color color) {
        BufferedImage image = new BufferedImage(VALID_WIDTH, VALID_HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < VALID_HEIGHT; y++) {
            for (int x = 0; x < VALID_WIDTH; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }

    private MockMultipartFile asMultipartFile(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "image.png", "image/png", output.toByteArray());
    }
}
