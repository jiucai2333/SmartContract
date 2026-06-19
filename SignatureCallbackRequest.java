package cupk.smartcontract.dto;

/**
 * 第三方签章平台异步回调 DTO。
 * 由法大大等平台在签章完成后回调本系统，无需登录认证。
 */
public record SignatureCallbackRequest(
        String transactionId,
        String status,
        String signedFileUrl,
        String signResult,
        Long timestamp,
        String sign
) {}
