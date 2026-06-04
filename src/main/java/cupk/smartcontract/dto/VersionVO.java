package cupk.smartcontract.dto;

import java.time.LocalDateTime;

public record VersionVO(
        Long versionId,
        Long contractId,
        String versionNo,
        String contentHash,
        String content,
        String contentPreview,
        String createdBy,
        LocalDateTime createdAt,
        Long fileId,
        String downloadUrl
) {}
