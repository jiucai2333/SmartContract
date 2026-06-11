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
    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<TemplateVO> listTemplates(@RequestParam(required = false) String type,
                                          @RequestParam(required = false) String keyword) {
        return templateService.list(type, keyword).stream().map(templateService::toVo).toList();
    }

    @GetMapping("/{id}")
    public TemplateVO getTemplate(@PathVariable Long id) {
        ContractTemplate template = templateService.get(id);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        return templateService.toVo(template);
    }

    @PostMapping
    @RequireRole({"LEGAL", "ADMIN"})
    public TemplateVO createTemplate(@RequestParam("file") MultipartFile file,
                                     @RequestParam("templateType") String templateType,
                                     @RequestParam("templateName") String templateName,
                                     @RequestParam(value = "description", required = false) String description,
                                     HttpServletRequest request) throws Exception {
        TemplateCreateRequest req = new TemplateCreateRequest(templateType, templateName, description);
        return templateService.toVo(templateService.create(req, file, ContractAttachmentService.currentUsername(request)));
    }

    @PutMapping("/{id}")
    @RequireRole({"LEGAL", "ADMIN"})
    public TemplateVO updateTemplate(@PathVariable Long id,
                                     @RequestParam(value = "file", required = false) MultipartFile file,
                                     @RequestParam("templateType") String templateType,
                                     @RequestParam("templateName") String templateName,
                                     @RequestParam(value = "description", required = false) String description,
                                     HttpServletRequest request) throws Exception {
        TemplateCreateRequest req = new TemplateCreateRequest(templateType, templateName, description);
        return templateService.toVo(templateService.update(id, req, file, ContractAttachmentService.currentUsername(request)));
    }

    @DeleteMapping("/{id}")
    @RequireRole({"LEGAL", "ADMIN"})
    public void deleteTemplate(@PathVariable Long id) {
        templateService.delete(id);
    }

    @GetMapping("/types")
    public List<String> listTemplateTypes() {
        return templateService.listTypes();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return templateService.download(id);
    }
}
