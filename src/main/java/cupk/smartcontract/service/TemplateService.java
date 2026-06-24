package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.dto.TemplateCreateRequest;
import cupk.smartcontract.dto.TemplateVO;
import cupk.smartcontract.entity.ContractTemplate;
import cupk.smartcontract.entity.FileInfo;
import cupk.smartcontract.mapper.ContractTemplateMapper;
import cupk.smartcontract.mapper.FileInfoMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TemplateService {
    private static final long MAX_TEMPLATE_FILE_SIZE = 200L * 1024 * 1024;
    private static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("CONFIDENTIAL", "保密合同"),
            Map.entry("LABOR", "劳务合同"),
            Map.entry("PURCHASE", "采购合同"),
            Map.entry("SALES", "销售合同"),
            Map.entry("TECH", "技术合同"),
            Map.entry("LOGISTICS", "物流合同"),
            Map.entry("ENTERPRISE_SERVICE", "企业服务合同"),
            Map.entry("INTELLECTUAL_PROPERTY", "知识产权合同")
    );

    private final ContractTemplateMapper templateMapper;
    private final FileInfoMapper fileInfoMapper;
    private final FileStorageService fileStorageService;
    private final DocumentParseService documentParseService;

    public TemplateService(ContractTemplateMapper templateMapper,
                           FileInfoMapper fileInfoMapper,
                           FileStorageService fileStorageService,
                           DocumentParseService documentParseService) {
        this.templateMapper = templateMapper;
        this.fileInfoMapper = fileInfoMapper;
        this.fileStorageService = fileStorageService;
        this.documentParseService = documentParseService;
    }

    public List<ContractTemplate> list(String type, String keyword) {
        return templateMapper.selectList(new LambdaQueryWrapper<ContractTemplate>()
                .eq(StringUtils.hasText(type), ContractTemplate::getTemplateType, type)
                .and(StringUtils.hasText(keyword), w -> w.like(ContractTemplate::getTemplateName, keyword)
                        .or().like(ContractTemplate::getDescription, keyword))
                .orderByDesc(ContractTemplate::getUpdatedAt));
    }

    public ContractTemplate get(Long id) {
        return templateMapper.selectById(id);
    }

    public ContractTemplate create(TemplateCreateRequest req, MultipartFile file, String username) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("璇蜂笂浼犳ā鏉挎枃浠?");
        }
        validateTemplateType(req.templateType());
        ContractTemplate template = new ContractTemplate();
        template.setTemplateType(req.templateType());
        template.setTemplateName(req.templateName());
        template.setDescription(req.description());
        template.setFileId(storeTemplateFile(file));
        template.setCreatedBy(username);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedBy(username);
        template.setUpdatedAt(LocalDateTime.now());
        template.setDeleted(0);
        template.setVersion(1);
        templateMapper.insert(template);
        return template;
    }

    public ContractTemplate update(Long id, TemplateCreateRequest req, MultipartFile file, String username) throws Exception {
        ContractTemplate template = get(id);
        if (template == null) {
            throw new IllegalArgumentException("妯℃澘涓嶅瓨鍦?");
        }
        validateTemplateType(req.templateType());
        if (file != null && !file.isEmpty()) {
            template.setFileId(storeTemplateFile(file));
        }
        template.setTemplateType(req.templateType());
        template.setTemplateName(req.templateName());
        template.setDescription(req.description());
        template.setUpdatedBy(username);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
        return template;
    }

    public void delete(Long id) {
        ContractTemplate template = get(id);
        if (template != null) {
            template.setDeleted(1);
            templateMapper.updateById(template);
        }
    }

    public List<String> listTypes() {
        return templateMapper.selectList(null).stream()
                .map(ContractTemplate::getTemplateType)
                .distinct()
                .sorted()
                .toList();
    }

    public TemplateVO toVo(ContractTemplate template) {
        String templateName = displayTemplateName(template);
        String fileName = null;
        Long fileSize = null;
        String downloadUrl = null;
        if (template.getFileId() != null) {
            FileInfo file = fileInfoMapper.selectById(template.getFileId());
            if (file != null) {
                fileName = displayFileName(template, file);
                fileSize = file.getSize();
                downloadUrl = "/api/templates/" + template.getTemplateId() + "/download";
            }
        }
        return new TemplateVO(template.getTemplateId(), template.getTemplateType(), templateName,
                template.getDescription(), template.getFileId(), fileName, fileSize,
                template.getCreatedBy(), template.getCreatedAt(),
                template.getUpdatedBy(), template.getUpdatedAt(), downloadUrl);
    }

    public ResponseEntity<Resource> download(Long id) {
        ContractTemplate template = get(id);
        if (template == null || template.getFileId() == null) {
            throw new IllegalArgumentException("妯℃澘鏂囦欢涓嶅瓨鍦?");
        }
        FileInfo file = fileInfoMapper.selectById(template.getFileId());
        if (file == null) {
            throw new IllegalArgumentException("妯℃澘鏂囦欢涓嶅瓨鍦?");
        }
        Path path = fileStorageService.resolve(file.getObjectKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(displayFileName(template, file)))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }

    public DocumentParseService.ParseResult parse(Long id, boolean preserveFormat) throws Exception {
        ContractTemplate template = get(id);
        if (template == null || template.getFileId() == null) {
            throw new IllegalArgumentException("模板文件不存在");
        }
        FileInfo file = fileInfoMapper.selectById(template.getFileId());
        if (file == null) {
            throw new IllegalArgumentException("模板文件不存在");
        }
        return documentParseService.parse(fileStorageService.resolve(file.getObjectKey()),
                file.getFileType(), preserveFormat);
    }

    private Long storeTemplateFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("璇蜂笂浼犳ā鏉挎枃浠?");
        }
        if (file.getSize() > MAX_TEMPLATE_FILE_SIZE) {
            throw new IllegalArgumentException("妯℃澘鏂囦欢涓嶈兘瓒呰繃 200MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") && !name.endsWith(".doc") && !name.endsWith(".docx")) {
            throw new IllegalArgumentException("妯℃澘鏂囦欢浠呮敮鎸?PDF銆丏OC 鎴?DOCX");
        }
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectKey(stored.objectKey());
        fileInfo.setFileName(file.getOriginalFilename());
        fileInfo.setFileType(stored.fileType());
        fileInfo.setSize(stored.size());
        fileInfo.setSha256(stored.sha256());
        fileInfo.setCreatedBy("template");
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfo.setDeleted(0);
        fileInfo.setVersion(1);
        fileInfoMapper.insert(fileInfo);
        return fileInfo.getFileId();
    }

    private String displayTemplateName(ContractTemplate template) {
        if (StringUtils.hasText(template.getTemplateName())) {
            return template.getTemplateName();
        }
        return TYPE_LABELS.getOrDefault(template.getTemplateType(), "合同") + "模板";
    }

    private void validateTemplateType(String templateType) {
        if (!TYPE_LABELS.containsKey(templateType)) {
            throw new IllegalArgumentException("涓嶆敮鎸佺殑妯℃澘鍒嗙被");
        }
    }

    private String displayFileName(ContractTemplate template, FileInfo file) {
        if (StringUtils.hasText(file.getFileName())) {
            return file.getFileName();
        }
        String ext = StringUtils.hasText(file.getFileType()) ? "." + file.getFileType() : ".docx";
        return displayTemplateName(template) + ext;
    }

    private String contentDisposition(String filename) {
        String safeName = filename == null ? "file" : filename.replace("\"", "'");
        String encodedName = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeName + "\"; filename*=UTF-8''" + encodedName;
    }
}
