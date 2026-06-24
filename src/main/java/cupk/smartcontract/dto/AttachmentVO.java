package cupk.smartcontract.dto;

import java.time.LocalDateTime;

/**
 * 附件元数据 VO。
 * 仅包含附件基本信息，不含 OCR/导入结果（OCR 结果见 {@link ContractImportResultVO}）。
 */
public record AttachmentVO(
        Long attachmentId,
        Long contractId,
        Long fileId,
        String fileName,
        String fileType,
        Long fileSize,
        String attachType,
        String downloadUrl,
        LocalDateTime createdAt
) {
}
