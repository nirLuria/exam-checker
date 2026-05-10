package com.examchecker.service;

import org.springframework.stereotype.Service;

@Service
public class MathTextNormalizer {

    public String normalize(String rawText) {
        if (rawText == null) {
            return "";
        }

        String text = rawText;

        // Normalize common math operators
        text = text.replace("×", "*");
        text = text.replace("x", "*");
        text = text.replace("X", "*");

        text = text.replace("÷", "/");
        text = text.replace(":", "/");

        text = text.replace("−", "-");
        text = text.replace("–", "-");
        text = text.replace("—", "-");

        text = text.replace("＝", "=");

        // Remove spaces
        text = text.replaceAll("\\s+", "");

        // Normalize Hebrew/visual noise if needed later
        text = text.trim();

        return text;
    }
}