package com.examchecker.infrastructure;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component
public class TesseractOcrService implements OcrService {

    @Override
    public String extractText(byte[] imageContent, String contentType) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("ocr-upload-", suffix(contentType));
            Files.write(tempFile.toPath(), imageContent);

            ITesseract tesseract = new Tesseract();

            // 🔥 זה החלק הכי חשוב
            tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");

            String text = tesseract.doOCR(tempFile);

            return text.trim();

        } catch (IOException e) {
            throw new RuntimeException("File handling failed", e);
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed", e);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    private String suffix(String contentType) {
        if ("image/png".equals(contentType)) return ".png";
        if ("image/jpeg".equals(contentType)) return ".jpg";
        if ("image/webp".equals(contentType)) return ".webp";
        return ".img";
    }
}
