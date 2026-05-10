package com.examchecker.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MockOcrService implements OcrService {

    @Override
    public String extractText(MultipartFile file) {
        return "1+1=2";
    }
}