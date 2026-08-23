package com.examchecker.infrastructure;

import org.springframework.stereotype.Component;

@Component
public class MockOcrService implements OcrService {

    @Override
    public String extractText(byte[] imageContent, String contentType) {
        return "1+1=2";
    }
}
