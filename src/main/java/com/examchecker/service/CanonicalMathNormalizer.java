package com.examchecker.service;


import org.springframework.stereotype.Component;

@Component
public class CanonicalMathNormalizer {

    public String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text
                .trim()
                .replaceAll("\\s+", "")
                .replace("×", "*")
                .replace("x", "*")
                .replace("X", "*")
                .replace("÷", "/")
                .replace(":", "/")
                .replace("\\div", "/")
                .replaceAll("^\\(?\\d+\\)?[.)-]", "");
    }
}
