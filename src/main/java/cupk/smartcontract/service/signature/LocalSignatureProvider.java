package cupk.smartcontract.service.signature;

import cupk.smartcontract.dto.SignatureRequest;
import cupk.smartcontract.dto.SignatureResponse;
import cupk.smartcontract.dto.VerificationRequest;
import cupk.smartcontract.dto.VerificationResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 本地签名实现（开发/离线模式）。
 * 不调用外部签章平台，生成模拟的签章哈希作为签名数据。
 * 当 signature.provider=local 时生效。
 */
@Service("localSignatureProvider")
public class LocalSignatureProvider implements SignatureProvider {

    @Override
    public SignatureResponse sign(SignatureRequest request) {
        String transactionId = "txn-" + UUID.randomUUID().toString().substring(0, 8);
        String raw = transactionId + request.contractId() + request.fileId() + System.currentTimeMillis();
        String simulatedHash = sha256Hex(raw);
        return new SignatureResponse(
                transactionId,
                "SIGNED",
                null,
                LocalDateTime.now(),
                "签名哈希: " + simulatedHash.substring(0, 16) + "..."
        );
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        return VerificationResult.valid("本地签章用户", LocalDateTime.now());
    }

    @Override
    public SignatureResponse queryStatus(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return new SignatureResponse(null, "INVALID", null, null, "事务 ID 为空");
        }
        return new SignatureResponse(transactionId, "SIGNED", null, LocalDateTime.now(), null);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }
}
