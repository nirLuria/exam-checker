package com.examchecker.infrastructure.ocr.core;

import org.springframework.web.multipart.MultipartFile;

public interface OcrEngine {

    OcrEngineMetadata metadata();

    String extractRaw(MultipartFile file);
}
