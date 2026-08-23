package com.examchecker.infrastructure.ocr.preparation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public final class QuestionImage {

    private final byte[] content;
    private final String contentType;
    private final String sha256;
    private final int width;
    private final int height;

    @JsonCreator
    public QuestionImage(
            @JsonProperty("content") byte[] content,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("sha256") String sha256,
            @JsonProperty("width") int width,
            @JsonProperty("height") int height
    ) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        this.content = content.clone();
        this.contentType = requireSupportedContentType(contentType);
        String calculatedHash = sha256(content);
        if (sha256 != null && !sha256.isBlank() && !calculatedHash.equalsIgnoreCase(sha256)) {
            throw new IllegalArgumentException("sha256 does not match image content");
        }
        this.sha256 = calculatedHash;
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        this.width = width;
        this.height = height;
    }

    public static QuestionImage create(byte[] content, String contentType, int width, int height) {
        return new QuestionImage(content, contentType, null, width, height);
    }

    @JsonProperty("content")
    public byte[] content() {
        return content.clone();
    }

    @JsonProperty("contentType")
    public String contentType() {
        return contentType;
    }

    @JsonProperty("sha256")
    public String sha256() {
        return sha256;
    }

    @JsonProperty("width")
    public int width() {
        return width;
    }

    @JsonProperty("height")
    public int height() {
        return height;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QuestionImage that)) return false;
        return width == that.width
                && height == that.height
                && Arrays.equals(content, that.content)
                && contentType.equals(that.contentType)
                && sha256.equals(that.sha256);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(contentType, sha256, width, height);
        return 31 * result + Arrays.hashCode(content);
    }

    private static String requireSupportedContentType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("image/png")
                && !normalized.equals("image/jpeg")
                && !normalized.equals("image/webp")) {
            throw new IllegalArgumentException("Unsupported image contentType: " + value);
        }
        return normalized;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
