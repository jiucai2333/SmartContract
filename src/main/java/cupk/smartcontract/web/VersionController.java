package cupk.smartcontract.web;

import cupk.smartcontract.domain.ContractVersion;
import cupk.smartcontract.dto.SaveVersionRequest;
import cupk.smartcontract.dto.VersionVO;
import cupk.smartcontract.service.ContractAttachmentService;
import cupk.smartcontract.service.ContractVersionService;
import cupk.smartcontract.service.StatusTransitionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VersionController {

    private final ContractVersionService service;
    private final StatusTransitionService statusTransitionService;

    public VersionController(ContractVersionService service,
                             StatusTransitionService statusTransitionService) {
        this.service = service;
        this.statusTransitionService = statusTransitionService;
    }

    @PostMapping("/contracts/{contractId}/versions")
    public VersionVO saveVersion(@PathVariable Long contractId,
                                  @Valid @RequestBody SaveVersionRequest req,
                                  HttpServletRequest request) {
        String username = ContractAttachmentService.currentUsername(request);
        ContractVersion version = service.create(contractId, req.content(),
                req.contentHash(), req.saveType(), username);
        return service.toVo(version);
    }

    @GetMapping("/contracts/{contractId}/versions")
    public List<VersionVO> listVersions(@PathVariable Long contractId) {
        return service.listByContract(contractId).stream().map(service::toVo).toList();
    }

    @GetMapping("/contracts/{contractId}/versions/{versionId}")
    public VersionVO getVersion(@PathVariable Long contractId,
                                 @PathVariable Long versionId) {
        ContractVersion v = service.get(versionId);
        if (v == null) throw new IllegalArgumentException("版本不存在");
        return service.toVo(v);
    }

    @PostMapping("/contracts/{contractId}/versions/{versionId}/restore")
    public ResponseEntity<?> restoreVersion(@PathVariable Long contractId,
                                     @PathVariable Long versionId,
                                     HttpServletRequest request) {
        if (statusTransitionService.isVersionLocked(versionId)) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "该版本已归档锁定，不可恢复"));
        }
        String username = ContractAttachmentService.currentUsername(request);
        ContractVersion restored = service.restore(contractId, versionId, username);
        return ResponseEntity.ok(service.toVo(restored));
    }
}
