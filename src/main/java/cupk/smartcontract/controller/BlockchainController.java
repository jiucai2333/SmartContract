package cupk.smartcontract.controller;

import cupk.smartcontract.entity.BlockchainRecord;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.AuditOperation;
import cupk.smartcontract.service.BlockchainService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 区块链防篡改 REST API。
 * 提供上链锚定、完整性验证和记录查询端点。
 */
@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {

    private final BlockchainService blockchainService;

    public BlockchainController(BlockchainService blockchainService) {
        this.blockchainService = blockchainService;
    }

    /**
     * 触发合同数据上链锚定。
     */
    @RequireRole({"LEGAL", "ADMIN"})
    @PostMapping("/anchor/{contractId}")
    @AuditOperation(operation = "BLOCKCHAIN_ANCHOR", targetType = "CONTRACT",
            targetIdParameter = "contractId")
    public ResponseEntity<?> anchor(@PathVariable Long contractId,
                                    @RequestParam(required = false) Long versionId) {
        try {
            BlockchainRecord record = blockchainService.anchorToChain(contractId, versionId);
            return ResponseEntity.ok(Map.of(
                    "message", "锚定成功",
                    "recordId", record.getRecordId(),
                    "nodeHash", record.getNodeHash()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "锚定失败: " + ex.getMessage()));
        }
    }

    /**
     * 验证合同哈希链完整性。
     */
    @GetMapping("/verify/{contractId}")
    public ResponseEntity<?> verify(@PathVariable Long contractId) {
        try {
            List<BlockchainService.IntegrityResult> results = blockchainService.verifyIntegrity(contractId);
            long failCount = results.stream().filter(r -> !r.valid()).count();
            return ResponseEntity.ok(Map.of(
                    "total", results.size(),
                    "failCount", failCount,
                    "allValid", failCount == 0,
                    "details", results));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证失败: " + ex.getMessage()));
        }
    }

    /**
     * 获取合同的所有区块链记录。
     */
    @GetMapping("/records/{contractId}")
    public List<BlockchainRecord> listRecords(@PathVariable Long contractId) {
        return blockchainService.getRecords(contractId);
    }

    /**
     * 清空并回填全部区块链记录（管理端点）。
     */
    @RequireRole({"ADMIN"})
    @PostMapping("/backfill")
    public ResponseEntity<?> backfill() {
        try {
            Map<String, Object> result = blockchainService.backfillAll();
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "回填失败: " + ex.getMessage()));
        }
    }
}
