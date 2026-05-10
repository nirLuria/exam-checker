package com.examchecker.infrastructure;

import org.springframework.web.multipart.MultipartFile;

public interface OcrService {
    String extractText(MultipartFile file);
}