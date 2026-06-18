package cupk.smartcontract.dto;

import java.time.LocalDateTime;

public record SealCreateRequest(
        Long contractId,
        Long versionId,
        Long fileId,
        String fileUrl,
        String fileName,
        String sealStatus,
        LocalDateTime sealTime,
        String remark
) {}
