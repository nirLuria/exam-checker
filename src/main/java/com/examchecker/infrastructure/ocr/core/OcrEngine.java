package com.examchecker.infrastructure.ocr.core;

import org.springframework.web.multipart.MultipartFile;

public interface OcrEngine {

    OcrEngineName name();

    OcrBundleResult extract(MultipartFile file);
}