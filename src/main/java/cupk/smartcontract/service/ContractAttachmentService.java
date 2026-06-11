package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.entity.ContractAttachment;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.FileInfo;
import cupk.smartcontract.dto.AttachmentVO;
import cupk.smartcontract.dto.ContractCreateRequest;
import cupk.smartcontract.dto.CreateContractFromOcrRequest;
import cupk.smartcontract.dto.OcrExtractVO;
import cupk.smartcontract.mapper.ContractAttachmentMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.FileInfoMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ContractAttachmentService {
    private final FileInfoMapper fileInfoMapper;
    private final ContractAttachmentMapper attachmentMapper;
    private final ContractMainMapper contractMainMapper;
    private final FileStorageService fileStorageService;
    private final DocumentParseService documentParseService;
    private final ContractManagementService contractManagementService;
    private final ObjectMapper objectMapper;

    public ContractAttachmentService(FileInfoMapper fileInfoMapper,
                                     ContractAttachmentMapper attachmentMapper,
                                     ContractMainMapper contractMainMapper,
                                     FileStorageService fileStorageService,
                                     DocumentParseService documentParseService,
                                     @Lazy ContractManagementService contractManagementService,
                                     ObjectMapper objectMapper) {
        this.fileInfoMapper = fileInfoMapper;
        this.attachmentMapper = attachmentMapper;
        this.contractMainMapper = contractMainMapper;
        this.fileStorageService = fileStorageService;
        this.documentParseService = documentParseService;
        this.contractManagementService = contractManagementService;
        this.objectMapper = objectMapper;
    }

    public AttachmentVO upload(MultipartFile file, Long contractId, boolean runOcr, String attachType, String createdBy) throws Exception {
        String safeAttachType = normalizeAttachType(attachType);
        boolean signedFile = "SIGNED_FILE".equals(safeAttachType);
        boolean shouldRunOcr = signedFile ? false : runOcr;
        validateUpload(file, signedFile);
        if (contractId != null) {
            contractManagementService.assertCanAccess(contractId);
        }
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        FileInfo fileInfo = findOrCreateFile(stored, file.getOriginalFilename());
        ContractAttachment attachment = new ContractAttachment();
        attachment.setContractId(contractId);
        attachment.setFileId(fileInfo.getFileId());
        attachment.setAttachType(safeAttachType);
        attachment.setOcrStatus(shouldRunOcr ? "PROCESSING" : "PENDING");
        attachment.setCreatedBy(createdBy);
        attachment.setCreatedAt(LocalDateTime.now());
        attachment.setUpdatedAt(LocalDateTime.now());
        attachment.setDeleted(0);
        attachment.setVersion(1);
        attachmentMapper.insert(attachment);
        if (shouldRunOcr) {
            runOcrInternal(attachment, fileInfo);
        }
        return toVo(attachment, fileInfo, contractId != null ? findContract(contractId) : null);
    }

    public AttachmentVO runOcr(Long attachmentId) throws Exception {
        ContractAttachment attachment = requireAccessibleAttachment(attachmentId);
        FileInfo fileInfo = requireFile(attachment.getFileId());
        attachment.setOcrStatus("PROCESSING");
        attachment.setOcrError(null);
        attachment.setUpdatedAt(LocalDateTime.now());
        attachmentMapper.updateById(attachment);
        runOcrInternal(attachment, fileInfo);
        return get(attachmentId);
    }

    public AttachmentVO get(Long attachmentId) {
        ContractAttachment attachment = requireAccessibleAttachment(attachmentId);
        FileInfo fileInfo = requireFile(attachment.getFileId());
        ContractMain contract = attachment.getContractId() != null ? findContract(attachment.getContractId()) : null;
        return toVo(attachment, fileInfo, contract);
    }

    public List<AttachmentVO> list(Long contractId, String ocrStatus) {
        if (contractId != null) {
            contractManagementService.assertCanAccess(contractId);
        }
        LambdaQueryWrapper<ContractAttachment> wrapper = new LambdaQueryWrapper<ContractAttachment>()
                .eq(contractId != null, ContractAttachment::getContractId, contractId)
                .eq(StringUtils.hasText(ocrStatus), ContractAttachment::getOcrStatus, ocrStatus)
                .orderByDesc(ContractAttachment::getCreatedAt);
        return attachmentMapper.selectList(wrapper).stream()
                .filter(this::canAccessAttachment)
                .map(att -> toVo(att, requireFile(att.getFileId()),
                        att.getContractId() != null ? findContract(att.getContractId()) : null))
                .toList();
    }

    public AttachmentVO link(Long attachmentId, Long contractId) {
        ContractAttachment attachment = requireAccessibleAttachment(attachmentId);
        findContract(contractId);
        contractManagementService.assertCanAccess(contractId);
        attachment.setContractId(contractId);
        attachment.setUpdatedAt(LocalDateTime.now());
        attachmentMapper.updateById(attachment);
        return get(attachmentId);
    }

    public ContractMain createContractFromOcr(CreateContractFromOcrRequest request) throws Exception {
        AttachmentVO attachment = get(request.attachmentId());
        OcrExtractVO ocr = attachment.ocrExtract();
        if (ocr == null) {
            throw new IllegalStateException("附件尚未完成 OCR 识别");
        }
        String title = StringUtils.hasText(request.title()) ? request.title() : defaultText(ocr.title(), "OCR识别合同");
        String counterparty = StringUtils.hasText(request.counterparty()) ? request.counterparty() : defaultText(ocr.counterparty(), ocr.partyB());
        String type = mapContractType(StringUtils.hasText(request.type()) ? request.type() : ocr.contractType());
        Long ownerId = SecurityContext.userId() != null ? SecurityContext.userId() : 1L;
        Long deptId = SecurityContext.deptId() != null ? SecurityContext.deptId() : 1L;
        ContractMain contract = contractManagementService.createContract(new ContractCreateRequest(
                title,
                type,
                ocr.amount() == null || ocr.amount().signum() <= 0 ? java.math.BigDecimal.valueOf(10000) : ocr.amount(),
                counterparty,
                deptId,
                ownerId,
                null,
                LocalDate.now().plusDays(90)
        ));
        link(request.attachmentId(), contract.getContractId());
        return contract;
    }

    public ResponseEntity<Resource> download(Long attachmentId) {
        ContractAttachment attachment = requireAccessibleAttachment(attachmentId);
        FileInfo fileInfo = requireFile(attachment.getFileId());
        Path path = fileStorageService.resolve(fileInfo.getObjectKey());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileInfo.getFileName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    public String resolveOcrReferenceText(Long attachmentId) {
        if (attachmentId == null) {
            return null;
        }
        try {
            AttachmentVO vo = get(attachmentId);
            if (vo.ocrExtract() != null && StringUtils.hasText(vo.ocrExtract().rawText())) {
                return vo.ocrExtract().rawText();
            }
            return vo.ocrTextPreview();
        } catch (Exception ex) {
            return null;
        }
    }

    public int countByContract(Long contractId) {
        contractManagementService.assertCanAccess(contractId);
        Long count = attachmentMapper.selectCount(
                new LambdaQueryWrapper<ContractAttachment>().eq(ContractAttachment::getContractId, contractId));
        return count == null ? 0 : count.intValue();
    }

    private void runOcrInternal(ContractAttachment attachment, FileInfo fileInfo) throws Exception {
        try {
            Path path = fileStorageService.resolve(fileInfo.getObjectKey());
            DocumentParseService.ParseResult result = documentParseService.parse(path, fileInfo.getFileType());
            attachment.setOcrStatus("SUCCESS");
            attachment.setOcrText(result.html());
            attachment.setOcrResult(null);
            attachment.setPageCount(result.pageCount());
            attachment.setOcrError(null);
        } catch (Exception ex) {
            attachment.setOcrStatus("FAILED");
            attachment.setOcrError(trimError(ex.getMessage()));
        }
        attachment.setUpdatedAt(LocalDateTime.now());
        attachmentMapper.updateById(attachment);
        if ("FAILED".equals(attachment.getOcrStatus())) {
            throw new IllegalStateException(attachment.getOcrError());
        }
    }

    private FileInfo findOrCreateFile(FileStorageService.StoredFile stored, String originalName) {
        FileInfo existing = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getSha256, stored.sha256()).last("LIMIT 1"));
        if (existing != null) {
            if (!existing.getObjectKey().equals(stored.objectKey())) {
                existing.setObjectKey(stored.objectKey());
                existing.setUpdatedAt(LocalDateTime.now());
                fileInfoMapper.updateById(existing);
            }
            return existing;
        }
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectKey(stored.objectKey());
        fileInfo.setFileName(originalName == null ? "upload." + stored.fileType() : originalName);
        fileInfo.setFileType(stored.fileType());
        fileInfo.setSize(stored.size());
        fileInfo.setSha256(stored.sha256());
        fileInfo.setCreatedBy(currentCreatedBy());
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfo.setDeleted(0);
        fileInfo.setVersion(1);
        fileInfoMapper.insert(fileInfo);
        return fileInfo;
    }

    private AttachmentVO toVo(ContractAttachment attachment, FileInfo fileInfo, ContractMain contract) {
        OcrExtractVO extract = parseOcrResult(attachment.getOcrResult());
        String fullText = attachment.getOcrText();
        String preview = fullText;
        if (preview != null && preview.length() > 200) {
            preview = preview.substring(0, 200) + "...";
        }
        return new AttachmentVO(
                attachment.getAttachmentId(),
                attachment.getContractId(),
                contract != null ? contract.getContractNo() : null,
                contract != null ? contract.getTitle() : null,
                fileInfo.getFileId(),
                fileInfo.getFileName(),
                fileInfo.getFileType(),
                fileInfo.getSize(),
                attachment.getAttachType(),
                attachment.getOcrStatus(),
                attachment.getOcrError(),
                extract,
                preview,
                fullText,
                attachment.getPageCount(),
                attachment.getCreatedBy(),
                attachment.getCreatedAt(),
                "/api/attachments/" + attachment.getAttachmentId() + "/download"
        );
    }

    private OcrExtractVO parseOcrResult(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            if (root.has("extract")) return root.path("extract").isNull()
                    ? null : objectMapper.treeToValue(root.path("extract"), OcrExtractVO.class);
            return objectMapper.treeToValue(root, OcrExtractVO.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private ContractAttachment requireAttachment(Long id) {
        ContractAttachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在");
        }
        return attachment;
    }

    private ContractAttachment requireAccessibleAttachment(Long id) {
        ContractAttachment attachment = requireAttachment(id);
        if (!canAccessAttachment(attachment)) {
            throw new SecurityException("无权访问该附件");
        }
        return attachment;
    }

    private boolean canAccessAttachment(ContractAttachment attachment) {
        if (attachment.getContractId() != null) {
            return contractManagementService.canAccess(findContract(attachment.getContractId()));
        }
        if ("ALL".equals(SecurityContext.dataScope())) {
            return true;
        }
        String createdBy = currentCreatedBy();
        return createdBy != null && createdBy.equals(attachment.getCreatedBy());
    }

    private FileInfo requireFile(Long fileId) {
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new IllegalArgumentException("文件不存在");
        }
        return fileInfo;
    }

    private ContractMain findContract(Long contractId) {
        ContractMain contract = contractMainMapper.selectById(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("合同不存在");
        }
        return contract;
    }

    private void validateUpload(MultipartFile file, boolean signedFile) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean pdf = name.endsWith(".pdf");
        boolean image = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
        long maxSize = signedFile && image ? 10L * 1024 * 1024 : 200L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(signedFile && image
                    ? "签章图片不能超过 10MB"
                    : "文件不能超过 200MB");
        }
        if (signedFile) {
            if (!pdf && !image) {
                throw new IllegalArgumentException("签章文件仅支持 PDF、JPG、JPEG、PNG");
            }
            return;
        }
        if (!pdf && !name.endsWith(".doc") && !name.endsWith(".docx")) {
            throw new IllegalArgumentException("仅支持 PDF、DOC 或 DOCX 文件");
        }
    }

    private String mapContractType(String contractType) {
        if (!StringUtils.hasText(contractType)) {
            return "TECH";
        }
        if (contractType.contains("采购")) return "PURCHASE";
        if (contractType.contains("销售")) return "SALES";
        if (contractType.contains("劳务")) return "LABOR";
        if (contractType.contains("技术")) return "TECH";
        return "TECH";
    }

    private String defaultText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String normalizeAttachType(String attachType) {
        if (!StringUtils.hasText(attachType)) {
            return "CONTRACT_FILE";
        }
        String value = attachType.trim().toUpperCase(Locale.ROOT);
        if ("SIGNED".equals(value)) {
            return "SIGNED_FILE";
        }
        if ("SIGNED_FILE".equals(value) || "ARCHIVE_FILE".equals(value) || "CONTRACT_FILE".equals(value)) {
            return value;
        }
        return "CONTRACT_FILE";
    }

    private String trimError(String message) {
        if (message == null) {
            return "OCR 识别失败";
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }

    private String contentDisposition(String filename) {
        String safeName = filename == null ? "file" : filename.replace("\"", "'");
        String encodedName = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeName + "\"; filename*=UTF-8''" + encodedName;
    }

    public static String currentUsername(HttpServletRequest request) {
        Object user = request.getAttribute("jwtUser");
        if (user instanceof cupk.smartcontract.dto.AuthUserVO authUser && StringUtils.hasText(authUser.username())) {
            return authUser.username();
        }
        return "anonymous";
    }

    public static Long currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("jwtUser");
        if (user instanceof cupk.smartcontract.dto.AuthUserVO authUser) {
            return authUser.userId();
        }
        return SecurityContext.userId();
    }

    public static String currentCreatedBy(HttpServletRequest request) {
        Long userId = currentUserId(request);
        return userId == null ? currentUsername(request) : String.valueOf(userId);
    }

    private String currentCreatedBy() {
        Long userId = SecurityContext.userId();
        return userId == null ? SecurityContext.username() : String.valueOf(userId);
    }
}
