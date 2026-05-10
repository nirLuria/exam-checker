package com.examchecker.infrastructure;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Component
public class TesseractOcrService implements OcrService {

    @Override
    public String extractText(MultipartFile file) {
        try {
            File tempFile = File.createTempFile("ocr-upload-", file.getOriginalFilename());
            file.transferTo(tempFile);

            ITesseract tesseract = new Tesseract();

            // 🔥 זה החלק הכי חשוב
            tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");

            String text = tesseract.doOCR(tempFile);

            tempFile.delete();

            return text.trim();

        } catch (IOException e) {
            throw new RuntimeException("File handling failed", e);
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed", e);
        }
    }
}