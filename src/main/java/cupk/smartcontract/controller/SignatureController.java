package cupk.smartcontract.controller;

import cupk.smartcontract.dto.*;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.service.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 电子签章 REST API。
 * 提供签章请求、状态查询、异步回调和签名验证端点。
 */
@RestController
@RequestMapping("/api/signature")
public class SignatureController {

    private static final Logger log = LoggerFactory.getLogger(SignatureController.class);

    private final SignatureService signatureService;

    public SignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    /**
     * 发起电子签章请求。
     */
    @PostMapping("/request")
    @RequireRole({"LEGAL", "DEPT_LEADER", "ADMIN"})
    @AuditOperation(operation = "SIGNATURE_REQUEST", targetType = "SEAL")
    public ResponseEntity<?> requestSignature(@RequestBody SignatureRequest request) {
        try {
            if (request.contractId() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "合同 ID 不能为空"));
            }
            if (request.fileId() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "文件 ID 不能为空"));
            }
            SignatureResponse response = signatureService.requestSignature(request);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * 查询签章事务状态。
     */
    @GetMapping("/status/{transactionId}")
    public ResponseEntity<?> getStatus(@PathVariable String transactionId) {
        try {
            SignatureResponse response = signatureService.getSignatureStatus(transactionId);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * 第三方签章平台异步回调（无需认证）。
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleCallback(@RequestBody SignatureCallbackRequest request) {
        try {
            log.info("收到签章回调: transactionId={}, status={}", request.transactionId(), request.status());
            signatureService.handleCallback(request);
            return ResponseEntity.ok(Map.of("code", 200, "msg", "回调处理成功"));
        } catch (Exception ex) {
            log.error("回调处理失败: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "msg", "回调处理失败: " + ex.getMessage()));
        }
    }

    /**
     * 验证签名有效性。
     */
    @PostMapping("/verify")
    @RequireRole({"LEGAL", "ADMIN"})
    public ResponseEntity<?> verifySignature(@RequestBody VerificationRequest request) {
        try {
            VerificationResult result = signatureService.verifySignature(request);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
