package cupk.smartcontract.dto;

import java.time.*;

public record TemplateVO(
        Long templateId,
        String templateType,
        String templateName,
        String description,
        Long fileId,
        String fileName,
        Long fileSize,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt,
        String downloadUrl
) {}
