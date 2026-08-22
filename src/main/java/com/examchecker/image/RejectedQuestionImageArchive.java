package com.examchecker.image;

import org.springframework.web.multipart.MultipartFile;

public interface RejectedQuestionImageArchive {

    ArchiveResult archive(MultipartFile file, ImageQualityReport report);

    record ArchiveResult(String archiveId, String relativeImagePath, boolean stored, String failureReason) {
        public static ArchiveResult stored(String archiveId, String relativeImagePath) {
            return new ArchiveResult(archiveId, relativeImagePath, true, "");
        }

        public static ArchiveResult failed(String failureReason) {
            return new ArchiveResult("", "", false, failureReason == null ? "" : failureReason);
        }

        public static ArchiveResult notRequired() {
            return new ArchiveResult("", "", false, "");
        }
    }
}
