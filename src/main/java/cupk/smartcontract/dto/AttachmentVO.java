package cupk.smartcontract.dto;

import java.time.LocalDateTime;

public record AttachmentVO(
        Long attachmentId,
        Long contractId,
        String contractNo,
        String contractTitle,
        Long fileId,
        String fileName,
        String fileType,
        Long fileSize,
        String attachType,
        String ocrStatus,
        String ocrError,
        OcrExtractResult ocrExtract,
        String ocrTextPreview,
        String ocrFullText,
        Integer pageCount,
        String createdBy,
        LocalDateTime createdAt,
        String downloadUrl
) {
}
