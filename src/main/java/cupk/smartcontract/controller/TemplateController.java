package cupk.smartcontract.controller;

import cupk.smartcontract.dto.TemplateCreateRequest;
import cupk.smartcontract.dto.TemplateVO;
import cupk.smartcontract.entity.ContractTemplate;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.service.ContractAttachmentService;
import cupk.smartcontract.service.TemplateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final TemplateService service;

    public TemplateController(TemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<TemplateVO> list(@RequestParam(required = false) String type,
                                 @RequestParam(required = false) String keyword) {
        return service.list(type, keyword).stream().map(service::toVo).toList();
    }

    @GetMapping("/{id}")
    public TemplateVO get(@PathVariable Long id) {
        ContractTemplate template = service.get(id);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        return service.toVo(template);
    }

    @PostMapping
    @RequireRole({"LEGAL", "ADMIN"})
    public TemplateVO create(@RequestParam("file") MultipartFile file,
                             @RequestParam("templateType") String templateType,
                             @RequestParam("templateName") String templateName,
                             @RequestParam(value = "description", required = false) String description,
                             HttpServletRequest request) throws Exception {
        TemplateCreateRequest req = new TemplateCreateRequest(templateType, templateName, description);
        return service.toVo(service.create(req, file, ContractAttachmentService.currentUsername(request)));
    }

    @PutMapping("/{id}")
    @RequireRole({"LEGAL", "ADMIN"})
    public TemplateVO update(@PathVariable Long id,
                             @RequestParam(value = "file", required = false) MultipartFile file,
                             @RequestParam("templateType") String templateType,
                             @RequestParam("templateName") String templateName,
                             @RequestParam(value = "description", required = false) String description,
                             HttpServletRequest request) throws Exception {
        TemplateCreateRequest req = new TemplateCreateRequest(templateType, templateName, description);
        return service.toVo(service.update(id, req, file, ContractAttachmentService.currentUsername(request)));
    }

    @DeleteMapping("/{id}")
    @RequireRole({"LEGAL", "ADMIN"})
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/types")
    public List<String> listTypes() {
        return service.listTypes();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return service.download(id);
    }
}
