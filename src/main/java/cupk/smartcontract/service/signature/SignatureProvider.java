package cupk.smartcontract.service.signature;

import cupk.smartcontract.dto.SignatureRequest;
import cupk.smartcontract.dto.SignatureResponse;
import cupk.smartcontract.dto.VerificationRequest;
import cupk.smartcontract.dto.VerificationResult;

/**
 * 签名提供者接口，定义电子签章的核心操作。
 * 不同实现对应不同签章平台（法大大、本地模拟等）。
 */
public interface SignatureProvider {

    /**
     * 发起电子签章请求。
     */
    SignatureResponse sign(SignatureRequest request);

    /**
     * 验证签名有效性。
     */
    VerificationResult verify(VerificationRequest request);

    /**
     * 查询签章事务状态。
     */
    SignatureResponse queryStatus(String transactionId);
}
