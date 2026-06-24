package cupk.smartcontract.dto;

/**
 * 签名验证请求 DTO。
 */
public record VerificationRequest(
        String transactionId,
        Long contractId
) {}
