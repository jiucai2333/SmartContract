package cupk.smartcontract.dto;

import java.time.LocalDateTime;

/**
 * 电子签章响应 DTO。
 * 由 SignatureProvider 实现类返回，包含签章平台的事务 ID 和结果状态。
 */
public record SignatureResponse(
        String transactionId,
        String status,
        String signedFileUrl,
        LocalDateTime signedAt,
        String errorMessage
) {
    public boolean isSuccess() {
        return "SIGNED".equalsIgnoreCase(status);
    }
}
