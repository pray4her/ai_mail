package com.github.mail.service.ai;

public record AiInputAttachment(
        Long attachmentId,
        String filename,
        String mimeType,
        String storagePath,
        String contentHash,
        String fallbackExtractedText
) {

    public boolean hasFallbackExtractedText() {
        return fallbackExtractedText != null && !fallbackExtractedText.isBlank();
    }

    public boolean isImage() {
        return mimeType != null && mimeType.toLowerCase().startsWith("image/");
    }

    public boolean isPdf() {
        return "application/pdf".equalsIgnoreCase(mimeType);
    }
}
