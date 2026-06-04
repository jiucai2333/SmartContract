package cupk.smartcontract.controller;

import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.entity.ContractVersion;
import cupk.smartcontract.dto.SaveVersionRequest;
import cupk.smartcontract.dto.VersionVO;
import cupk.smartcontract.service.ContractAttachmentService;
import cupk.smartcontract.service.ContractVersionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class VersionController {

    private final ContractVersionService service;

    public VersionController(ContractVersionService service) {
        this.service = service;
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
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

    @GetMapping("/contracts/{contractId}/versions/latest")
    public VersionVO latestVersion(@PathVariable Long contractId) {
        ContractVersion version = service.latest(contractId);
        return version == null ? null : service.toVo(version);
    }

    @GetMapping("/contracts/{contractId}/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadVersion(@PathVariable Long contractId,
                                                    @PathVariable Long versionId) {
        return service.download(contractId, versionId);
    }

    @GetMapping("/contracts/{contractId}/versions/{versionId}")
    public VersionVO getVersion(@PathVariable Long contractId,
                                @PathVariable Long versionId) {
        service.listByContract(contractId);
        ContractVersion version = service.get(versionId);
        if (version == null) throw new IllegalArgumentException("版本不存在");
        if (!contractId.equals(version.getContractId())) throw new IllegalArgumentException("版本不属于此合同");
        return service.toVo(version);
    }

    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "ADMIN"})
    @PostMapping("/contracts/{contractId}/versions/{versionId}/restore")
    public VersionVO restoreVersion(@PathVariable Long contractId,
                                    @PathVariable Long versionId,
                                    HttpServletRequest request) {
        String username = ContractAttachmentService.currentUsername(request);
        ContractVersion restored = service.restore(contractId, versionId, username);
        return service.toVo(restored);
    }
}
