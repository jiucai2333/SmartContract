package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.entity.ContractAttachment;
import cupk.smartcontract.entity.FileInfo;
import cupk.smartcontract.dto.AttachmentVO;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 纯附件 Service。
 * 只负责：文件上传存储、FileInfo 创建/复用、附件记录 CRUD、附件下载、合同关联。
 * OCR/Qwen/HTML 编排已全部迁移至 {@link ContractImportService}。
 */
@Service
public class ContractAttachmentService {

    private final FileInfoMapper fileInfoMapper;
    private final ContractAttachmentMapper attachmentMapper;
    private final ContractMainMapper contractMainMapper;
    private final FileStorageService fileStorageService;
    private final ContractManagementService contractManagementService;

    public ContractAttachmentService(FileInfoMapper fileInfoMapper,
                                     ContractAttachmentMapper attachmentMapper,
                                     ContractMainMapper contractMainMapper,
                                     FileStorageService fileStorageService,
                                     @Lazy ContractManagementService contractManagementService) {
        this.fileInfoMapper = fileInfoMapper;
        this.attachmentMapper = attachmentMapper;
        this.contractMainMapper = contractMainMapper;
        this.fileStorageService = fileStorageService;
        this.contractManagementService = contractManagementService;
    }

    // ==================== 附件上传 ====================

    /**
     * 纯附件上传：校验文件、存储、创建 ContractAttachment 和 FileInfo 记录。
     * 不执行 OCR，不创建 ContractAttachmentOcr。
     */
    public AttachmentCreatedResult createAttachment(MultipartFile file, Long contractId,
                                                     String attachType, String createdBy) throws Exception {
        String safeAttachType = normalizeAttachType(attachType);
        boolean signedFile = "SIGNED_FILE".equals(safeAttachType);
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
        attachment.setCreatedBy(createdBy);
        attachment.setUpdatedBy(createdBy);
        attachment.setCreatedAt(LocalDateTime.now());
        attachment.setUpdatedAt(LocalDateTime.now());
        attachment.setDeleted(0);
        attachment.setVersion(1);
        attachmentMapper.insert(attachment);
        return new AttachmentCreatedResult(attachment, fileInfo, safeAttachType, signedFile);
    }

    // ==================== 附件查询 ====================

    public AttachmentVO get(Long attachmentId) {
        ContractAttachment attachment = requireAccessibleAttachment(attachmentId);
        FileInfo fileInfo = requireFileInfo(attachment.getFileId());
        return toAttachmentVo(attachment, fileInfo);
    }

    public List<AttachmentVO> list(Long contractId, String ocrStatus) {
        if (contractId != null) {
            contractManagementService.assertCanAccess(contractId);
        }
        return attachmentMapper.selectWithOcr(contractId, ocrStatus).stream()
                .filter(row -> canAccessAttachment(row.getAttachment()))
                .map(row -> toAttachmentVo(row.getAttachment(),
                        requireFileInfo(row.getAttachment().getFileId())))
                .toList();
    }

    public AttachmentVO link(Long attachmentId, Long contractId) {
        ContractAttachment attachment = requireAccessibleAttachment(attachmentId);
        contractManagementService.assertCanAccess(contractId);
        attachment.setContractId(contractId);
        attachment.setUpdatedAt(LocalDateTime.now());
        attachmentMapper.updateById(attachment);
        return get(attachmentId);
    }

    // ==================== 附件下载 ====================

    public ResponseEntity<Resource> download(Long attachmentId) {
        ContractAttachment attachment = requireAccessibleAttachment(attachmentId);
        FileInfo fileInfo = requireFileInfo(attachment.getFileId());
        Path path = fileStorageService.resolve(fileInfo.getObjectKey());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileInfo.getFileName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // ==================== 合同维度 ====================

    public int countByContract(Long contractId) {
        contractManagementService.assertCanAccess(contractId);
        Long count = attachmentMapper.selectCount(
                new LambdaQueryWrapper<ContractAttachment>()
                        .eq(ContractAttachment::getContractId, contractId)
                        .eq(ContractAttachment::getDeleted, 0));
        return count == null ? 0 : count.intValue();
    }

    // ==================== 供 ContractImportService 使用的 package-private 方法 ====================

    public AttachmentVO toAttachmentVo(ContractAttachment attachment, FileInfo fileInfo) {
        return new AttachmentVO(
                attachment.getAttachmentId(),
                attachment.getContractId(),
                fileInfo.getFileId(),
                fileInfo.getFileName(),
                fileInfo.getFileType(),
                fileInfo.getSize(),
                attachment.getAttachType(),
                "/api/attachments/" + attachment.getAttachmentId() + "/download",
                attachment.getCreatedAt()
        );
    }

    ContractAttachment requireAccessibleAttachment(Long id) {
        ContractAttachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在");
        }
        if (!canAccessAttachment(attachment)) {
            throw new SecurityException("无权访问该附件");
        }
        return attachment;
    }

    FileInfo requireFileInfo(Long fileId) {
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new IllegalArgumentException("文件不存在");
        }
        return fileInfo;
    }

    // ==================== 内部私有方法 ====================

    private boolean canAccessAttachment(ContractAttachment attachment) {
        if (attachment.getContractId() != null) {
            var contract = contractMainMapper.selectById(attachment.getContractId());
            if (contract == null) return false;
            return contractManagementService.canAccess(contract);
        }
        if ("ALL".equals(SecurityContext.dataScope())) {
            return true;
        }
        String createdBy = currentCreatedBy();
        return createdBy != null && createdBy.equals(attachment.getCreatedBy());
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

    private void validateUpload(MultipartFile file, boolean signedFile) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean pdf = name.endsWith(".pdf");
        boolean signedImage = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
        boolean contractImage = signedImage || name.endsWith(".webp");
        long maxSize = signedFile && signedImage ? 10L * 1024 * 1024 : 200L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(signedFile && signedImage
                    ? "签章图片不能超过 10MB"
                    : "文件不能超过 200MB");
        }
        if (signedFile) {
            if (!pdf && !signedImage) {
                throw new IllegalArgumentException("签章文件仅支持 PDF、JPG、JPEG、PNG");
            }
            validateFileSignature(file, name);
            return;
        }
        if (!pdf && !name.endsWith(".doc") && !name.endsWith(".docx") && !contractImage) {
            throw new IllegalArgumentException("仅支持 PDF、DOC、DOCX、JPG、JPEG、PNG 或 WEBP 文件");
        }
        validateFileSignature(file, name);
    }

    private void validateFileSignature(MultipartFile file, String name) {
        try {
            byte[] header;
            try (var input = file.getInputStream()) {
                header = input.readNBytes(16);
            }
            boolean valid;
            if (name.endsWith(".pdf")) {
                valid = startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                valid = header.length >= 3 && (header[0] & 0xff) == 0xff
                        && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            } else if (name.endsWith(".png")) {
                valid = header.length >= 8 && (header[0] & 0xff) == 0x89
                        && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47;
            } else if (name.endsWith(".webp")) {
                valid = header.length >= 12
                        && "RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                        && "WEBP".equals(new String(header, 8, 4, StandardCharsets.US_ASCII));
            } else if (name.endsWith(".docx")) {
                valid = header.length >= 4 && header[0] == 0x50 && header[1] == 0x4b;
            } else if (name.endsWith(".doc")) {
                valid = header.length >= 8 && (header[0] & 0xff) == 0xd0
                        && (header[1] & 0xff) == 0xcf && (header[2] & 0xff) == 0x11
                        && (header[3] & 0xff) == 0xe0;
            } else {
                valid = false;
            }
            if (!valid) {
                throw new IllegalArgumentException("文件内容与扩展名不匹配或文件格式不受支持");
            }
            String contentType = file.getContentType();
            if (StringUtils.hasText(contentType)
                    && !"application/octet-stream".equalsIgnoreCase(contentType)
                    && !mimeMatches(name, contentType)) {
                throw new IllegalArgumentException("文件 MIME 类型与扩展名不匹配");
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("无法读取上传文件进行格式校验", ex);
        }
    }

    private boolean mimeMatches(String name, String contentType) {
        String mime = contentType.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return mime.contains("pdf");
        if (name.endsWith(".docx")) return mime.contains("officedocument") || mime.contains("zip");
        if (name.endsWith(".doc")) return mime.contains("msword") || mime.contains("ole");
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return mime.contains("jpeg") || mime.contains("jpg");
        if (name.endsWith(".png")) return mime.contains("png");
        if (name.endsWith(".webp")) return mime.contains("webp");
        return false;
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) return false;
        }
        return true;
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

    private String contentDisposition(String filename) {
        String safeName = filename == null ? "file" : filename.replace("\"", "'");
        String encodedName = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeName + "\"; filename*=UTF-8''" + encodedName;
    }

    private String currentCreatedBy() {
        Long userId = SecurityContext.userId();
        return userId == null ? SecurityContext.username() : String.valueOf(userId);
    }

    // ==================== 静态工具方法 ====================

    public static String currentUsername(HttpServletRequest request) {
        Object user = request.getAttribute("jwtUser");
        if (user instanceof cupk.smartcontract.dto.AuthUserVO authUser && StringUtils.hasText(authUser.username())) {
            return authUser.username();
        }
        return "anonymous";
    }

    public static String currentCreatedBy(HttpServletRequest request) {
        Long userId = currentUserId(request);
        return userId == null ? currentUsername(request) : String.valueOf(userId);
    }

    private static Long currentUserId(HttpServletRequest request) {
        Object user = request.getAttribute("jwtUser");
        if (user instanceof cupk.smartcontract.dto.AuthUserVO authUser) {
            return authUser.userId();
        }
        return SecurityContext.userId();
    }

    // ==================== 数据传输记录 ====================

    public record AttachmentCreatedResult(ContractAttachment attachment, FileInfo fileInfo,
                                           String safeAttachType, boolean signedFile) {
    }
}
