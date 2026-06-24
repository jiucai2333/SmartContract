package cupk.smartcontract.dto;

import java.time.LocalDateTime;

/**
 * 签章验证结果 DTO。
 */
public record VerificationResult(
        boolean valid,
        String signerName,
        LocalDateTime signTime,
        String certificateInfo,
        String message
) {
    public static VerificationResult valid(String signerName, LocalDateTime signTime) {
        return new VerificationResult(true, signerName, signTime, "LOCAL_DEV", "签名验证通过");
    }

    public static VerificationResult invalid(String message) {
        return new VerificationResult(false, null, null, null, message);
    }
}
