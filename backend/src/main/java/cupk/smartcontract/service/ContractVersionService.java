package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.entity.ContractVersion;
import cupk.smartcontract.entity.FileInfo;
import cupk.smartcontract.dto.VersionVO;
import cupk.smartcontract.mapper.ContractVersionMapper;
import cupk.smartcontract.mapper.FileInfoMapper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContractVersionService {
    private static final Logger log = LoggerFactory.getLogger(ContractVersionService.class);

    private final ContractVersionMapper mapper;
    private final FileInfoMapper fileInfoMapper;
    private final FileStorageService fileStorageService;
    private final WordArchiveService wordArchiveService;
    private final DocumentParseService documentParseService;
    private final ContractManagementService contractManagementService;
    private final ContractDocumentImportService contractDocumentImportService;

    public ContractVersionService(ContractVersionMapper mapper, FileInfoMapper fileInfoMapper,
                                  FileStorageService fileStorageService, WordArchiveService wordArchiveService,
                                  DocumentParseService documentParseService,
                                  ContractManagementService contractManagementService,
                                  ContractDocumentImportService contractDocumentImportService) {
        this.mapper = mapper;
        this.fileInfoMapper = fileInfoMapper;
        this.fileStorageService = fileStorageService;
        this.wordArchiveService = wordArchiveService;
        this.documentParseService = documentParseService;
        this.contractManagementService = contractManagementService;
        this.contractDocumentImportService = contractDocumentImportService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVersion create(Long contractId, String content, String contentHash,
                                  String saveType, String username) {
        contractManagementService.assertCanAccess(contractId);
        String safeContent = contractDocumentImportService == null
                ? sanitizeHtml(content)
                : sanitizeHtml(contractDocumentImportService.normalizeEditorHtml(content));
        String hash = sha256(safeContent);

        contractManagementService.lockContract(contractId);
        String nextVersionNo = generateNextVersionNo(contractId);

        FileInfo fileInfo = archiveDocx(contractId, nextVersionNo, safeContent, username);
        ContractVersion version = new ContractVersion();
        version.setContractId(contractId);
        version.setVersionNo(nextVersionNo);
        version.setContentHash(hash);
        version.setFileId(fileInfo.getFileId());
        version.setContent(safeContent);
        version.setCreatedBy(username);
        version.setCreatedAt(LocalDateTime.now());
        version.setUpdatedBy(username);
        version.setUpdatedAt(LocalDateTime.now());
        version.setIsDeleted(0);
        version.setVersion(1);
        mapper.insert(version);
        return version;
    }

    public List<ContractVersion> listByContract(Long contractId) {
        contractManagementService.assertCanAccess(contractId);
        return mapper.selectList(new LambdaQueryWrapper<ContractVersion>()
                .eq(ContractVersion::getContractId, contractId)
                .orderByDesc(ContractVersion::getCreatedAt));
    }

    public ContractVersion get(Long versionId) {
        return mapper.selectById(versionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVersion restore(Long contractId, Long versionId, String username) {
        contractManagementService.assertCanAccess(contractId);
        ContractVersion oldVersion = get(versionId);
        if (oldVersion == null) throw new IllegalArgumentException("鐗堟湰涓嶅瓨鍦?");
        if (!oldVersion.getContractId().equals(contractId)) {
            throw new IllegalArgumentException("鐗堟湰涓嶅睘浜庢合同");
        }
        return create(contractId, loadHtml(oldVersion), oldVersion.getContentHash(), "SAVE", username);
    }

    public VersionVO toVo(ContractVersion version) {
        String html = loadHtml(version);
        String preview = html;
        if (preview != null && preview.length() > 150) {
            preview = preview.substring(0, 150).replaceAll("<[^>]+>", "") + "...";
        } else if (preview != null) {
            preview = preview.replaceAll("<[^>]+>", "");
        }
        return new VersionVO(
                version.getVersionId(), version.getContractId(), version.getVersionNo(),
                version.getContentHash(), html, preview,
                version.getCreatedBy(), version.getCreatedAt(), version.getFileId(),
                version.getIsLocked(),
                version.getFileId() == null ? null : "/api/contracts/" + version.getContractId()
                        + "/versions/" + version.getVersionId() + "/download"
        );
    }

    public ContractVersion latest(Long contractId) {
        List<ContractVersion> list = listByContract(contractId);
        return list.isEmpty() ? null : list.get(0);
    }

    public ResponseEntity<Resource> download(Long contractId, Long versionId) {
        contractManagementService.assertCanAccess(contractId);
        ContractVersion version = get(versionId);
        if (version == null || !version.getContractId().equals(contractId) || version.getFileId() == null) {
            throw new IllegalArgumentException("鑽夌鐗堟湰鏂囦欢涓嶅瓨鍦?");
        }
        FileInfo file = fileInfoMapper.selectById(version.getFileId());
        if (file == null) throw new IllegalArgumentException("草稿版本文件不存在");
        try {
            Resource resource = new FileSystemResource(fileStorageService.resolve(file.getObjectKey()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.getFileName()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IllegalArgumentException ex) {
            String html = loadHtml(version);
            if (!StringUtils.hasText(html)) {
                throw new IllegalArgumentException("草稿版本文件不存在，且无正文可重新生成");
            }
            try {
                String fallbackName = "contract-" + contractId + "-" + version.getVersionNo() + ".docx";
                ByteArrayResource resource = new ByteArrayResource(wordArchiveService.toDocx(html));
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fallbackName))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            } catch (Exception generateEx) {
                throw new IllegalStateException("草稿版本文件缺失，重新生成失败: " + generateEx.getMessage(), generateEx);
            }
        }
    }

    private FileInfo archiveDocx(Long contractId, String versionNo, String html, String username) {
        try {
            String filename = "contract-" + contractId + "-" + versionNo + ".docx";
            FileStorageService.StoredFile stored = fileStorageService.store(
                    wordArchiveService.toDocx(html), filename, "docx");
            registerRollbackCleanup(stored.objectKey());
            FileInfo file = new FileInfo();
            file.setObjectKey(stored.objectKey());
            file.setFileName(filename);
            file.setFileType(stored.fileType());
            file.setSize(stored.size());
            file.setSha256(stored.sha256());
            file.setCreatedBy(username);
            file.setCreatedAt(LocalDateTime.now());
            file.setUpdatedAt(LocalDateTime.now());
            file.setDeleted(0);
            file.setVersion(1);
            fileInfoMapper.insert(file);
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException("生成草稿 Word 失败: " + ex.getMessage(), ex);
        }
    }

    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Version files must be created inside a transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;
                try {
                    fileStorageService.delete(objectKey);
                } catch (Exception ex) {
                    log.error("Failed to remove rolled-back version file: {}", objectKey, ex);
                }
            }
        });
    }

    private String loadHtml(ContractVersion version) {
        if (StringUtils.hasText(version.getContent())) return sanitizeHtml(version.getContent());
        if (version.getFileId() == null) return "";
        FileInfo file = fileInfoMapper.selectById(version.getFileId());
        if (file == null) return "";
        try {
            return sanitizeHtml(documentParseService.parse(fileStorageService.resolve(file.getObjectKey()), file.getFileType()).html());
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * 根据合同已有的版本记录，生成下一个版本号。
     * 规则：从 V1.0 开始，每次递增主版本号，格式为 V{数字}.0。
     * 兼容解析历史数据中的小写 v 前缀（如 v1.0）。
     */
    private String generateNextVersionNo(Long contractId) {
        List<ContractVersion> list = mapper.selectList(
                new LambdaQueryWrapper<ContractVersion>()
                        .eq(ContractVersion::getContractId, contractId));

        int max = 0;
        Pattern pattern = Pattern.compile("^[vV]?(\\d+)(?:\\.0)?$");
        for (ContractVersion v : list) {
            String vn = v.getVersionNo();
            if (vn == null) continue;
            Matcher matcher = pattern.matcher(vn.trim());
            if (matcher.matches()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return "V" + (max + 1) + ".0";
    }

    String sanitizeHtml(String html) {
        String cleaned = Jsoup.clean(html == null ? "" : html, Safelist.relaxed()
                .addAttributes("p", "style", "data-text-indent", "data-indent",
                        "data-doc-style", "data-align", "data-indent-chars",
                        "data-page", "data-bbox", "data-column")
                .addAttributes("p", "class")
                .addAttributes("div", "class", "style", "data-page-no", "data-block-id",
                        "data-block-type", "data-align", "data-font-size-level",
                        "data-bbox-source", "data-confidence", "data-text-indent", "data-indent",
                        "data-doc-style", "data-indent-chars", "data-page", "data-bbox",
                        "data-column")
                .addAttributes("h1", "style", "data-doc-style", "data-align",
                        "data-indent-chars", "data-page", "data-bbox", "data-column")
                .addAttributes("h2", "style", "data-doc-style", "data-align",
                        "data-indent-chars", "data-page", "data-bbox", "data-column")
                .addAttributes("h3", "style", "data-doc-style", "data-align",
                        "data-indent-chars", "data-page", "data-bbox", "data-column")
                .addAttributes("h4", "style", "data-doc-style", "data-align",
                        "data-indent-chars", "data-page", "data-bbox", "data-column")
                .addAttributes("h5", "style", "data-doc-style", "data-align",
                        "data-indent-chars", "data-page", "data-bbox", "data-column")
                .addAttributes("h6", "style", "data-doc-style", "data-align",
                        "data-indent-chars", "data-page", "data-bbox", "data-column")
                .addAttributes("span", "class", "style", "data-placeholder-text", "data-placeholder-empty")
                .addAttributes("table", "class", "style")
                .addAttributes("td", "colspan", "rowspan", "style")
                .addAttributes("th", "colspan", "rowspan", "style")
                .addAttributes("a", "href", "title")
                .addProtocols("a", "href", "http", "https", "mailto"));
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(cleaned);
        document.select("[style]").forEach(element -> {
            String safeStyle = java.util.Arrays.stream(element.attr("style").split(";"))
                    .map(String::trim)
                    .filter(this::isSafeEditorStyle)
                    .collect(java.util.stream.Collectors.joining(";"));
            if (safeStyle.isEmpty()) element.removeAttr("style");
            else element.attr("style", safeStyle);
        });
        return document.body().html();
    }

    private boolean isSafeEditorStyle(String rule) {
        if (rule == null || rule.isBlank()) return false;
        int separator = rule.indexOf(':');
        if (separator <= 0) return false;
        String property = rule.substring(0, separator).trim().toLowerCase();
        String value = rule.substring(separator + 1).trim();
        return switch (property) {
            case "text-indent" -> value.matches("(?i)^-?\\d+(?:\\.\\d+)?(?:em|px|pt|%)?$");
            case "text-align" -> value.matches("(?i)^(left|right|center|justify)$");
            case "font-size" -> value.matches("(?i)^\\d+(?:\\.\\d+)?(?:px|pt|em|rem|%)$");
            case "font-weight" -> value.matches("(?i)^(normal|bold|[1-9]00)$");
            case "font-family" -> value.matches("^[\\w\\s,'\"\\-\\u4e00-\\u9fa5]+$");
            case "line-height" -> value.matches("(?i)^\\d+(?:\\.\\d+)?(?:px|pt|em|rem|%)?$");
            case "display" -> value.matches("(?i)^(inline|inline-block|block)$");
            case "min-width", "padding" -> value.matches("(?i)^[\\d.]+(?:px|pt|em|rem|%)?(?:\\s+[\\d.]+(?:px|pt|em|rem|%)?){0,3}$");
            case "border-bottom" -> value.matches("(?i)^\\d+(?:\\.\\d+)?px\\s+(?:solid|dashed|dotted)\\s+(?:currentColor|#[0-9a-f]{3,8}|[a-z]+)$");
            case "color", "background-color" -> value.matches(
                    "(?i)^(#[0-9a-f]{3,8}|rgb\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*\\)|rgba\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*(?:0|1|0?\\.\\d+)\\s*\\)|[a-z]+)$");
            default -> false;
        };
    }

    private String contentDisposition(String filename) {
        String safeName = filename == null ? "file" : filename.replace("\"", "'");
        String encodedName = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeName + "\"; filename*=UTF-8''" + encodedName;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
