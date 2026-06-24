package cupk.smartcontract.dto;

import java.time.LocalDateTime;

public record FulfillmentVoucherVO(
        Long voucherId,
        Long planId,
        Long contractId,
        String nodeName,
        String planType,
        Long fileId,
        String fileName,
        String fileType,
        Long fileSize,
        String voucherType,
        String reviewStatus,
        Long uploadedBy,
        String uploadedByName,
        LocalDateTime uploadedAt,
        Long reviewerId,
        String reviewerName,
        LocalDateTime reviewedAt,
        String remark,
        String downloadUrl
) {
}
