package cupk.smartcontract.service.signature;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.SignatureProperties;
import cupk.smartcontract.dto.SignatureRequest;
import cupk.smartcontract.dto.SignatureResponse;
import cupk.smartcontract.dto.VerificationRequest;
import cupk.smartcontract.dto.VerificationResult;
import cupk.smartcontract.mapper.FileInfoMapper;
import cupk.smartcontract.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 法大大 FASC v5 API 对接实现（UAT 测试环境）。
 * 严格对齐官方 API 文档参数名和结构。
 */
@Service("fadadaSignatureProvider")
public class FadadaSignatureProvider implements SignatureProvider {

    private static final Logger log = LoggerFactory.getLogger(FadadaSignatureProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUB_VERSION = "5.1";
    private static final String SIGN_TYPE = "HMAC-SHA256";

    private final SignatureProperties props;
    private final RestTemplate rest;
    private final FileInfoMapper fileInfoMapper;
    private final FileStorageService fileStorageService;

    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    public FadadaSignatureProvider(SignatureProperties props,
                                   FileInfoMapper fileInfoMapper,
                                   FileStorageService fileStorageService) {
        this.props = props;
        this.rest = new RestTemplate();
        this.fileInfoMapper = fileInfoMapper;
        this.fileStorageService = fileStorageService;
    }

    // ==================== SignatureProvider 实现 ====================

    @Override
    public SignatureResponse sign(SignatureRequest request) {
        try {
            String token = getAccessToken();
            // 步骤1: 上传文件到法大大
            String fddFileId = uploadFile(token, request.fileId());
            if (fddFileId == null) return err("文件上传失败");
            // 步骤2: 创建签署任务
            String signTaskId = createSignTask(token, fddFileId, request);
            if (signTaskId == null) return err("创建签署任务失败");
            // 步骤3: 提交签署任务
            boolean ok = startSignTask(token, signTaskId);
            if (!ok) return err("提交签署任务失败");
            // 步骤4: 获取签署链接
            String signUrl = getActorSignUrl(token, signTaskId);
            log.info("签章任务创建成功: signTaskId={}, signUrl={}", signTaskId, signUrl);
            return new SignatureResponse(signTaskId, "PENDING", signUrl, LocalDateTime.now(), null);
        } catch (Exception e) {
            log.error("签章流程异常", e);
            return err(e.getMessage());
        }
    }

    @Override
    public SignatureResponse queryStatus(String signTaskId) {
        try {
            String token = getAccessToken();
            Map<String, Object> result = call(token, "/sign-task/get-detail",
                    Map.of("signTaskId", signTaskId));
            if (result != null) {
                String status = mapStatus(String.valueOf(result.getOrDefault("signTaskStatus", "")));
                String dl = null;
                if ("SIGNED".equals(status)) dl = getDownloadUrl(token, signTaskId);
                return new SignatureResponse(signTaskId, status, dl, LocalDateTime.now(), null);
            }
            return new SignatureResponse(signTaskId, "UNKNOWN", null, null, "查询无结果");
        } catch (Exception e) {
            return new SignatureResponse(signTaskId, "ERROR", null, null, e.getMessage());
        }
    }

    @Override
    public VerificationResult verify(VerificationRequest req) {
        return VerificationResult.valid("法大大平台", LocalDateTime.now());
    }

    // ==================== 法大大 v5 API 调用 ====================

    private synchronized String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000) {
            log.debug("使用缓存的 accessToken");
            return cachedToken;
        }
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("X-FASC-App-Id", props.fadadaAppId());
            params.put("X-FASC-Sign-Type", SIGN_TYPE);
            params.put("X-FASC-Timestamp", String.valueOf(System.currentTimeMillis()));
            params.put("X-FASC-Nonce", UUID.randomUUID().toString().replace("-", ""));
            params.put("X-FASC-Grant-Type", "client_credential");
            params.put("X-FASC-Api-SubVersion", SUB_VERSION);

            log.info("请求法大大 Token: appId={}, url={}, timestamp={}",
                    props.fadadaAppId(), props.fadadaApiUrl(), params.get("X-FASC-Timestamp"));

            HttpHeaders headers = buildHeaders(params);
            String url = props.fadadaApiUrl() + "/service/get-access-token";
            log.debug("Token 请求 headers: X-FASC-Sign={}", headers.getFirst("X-FASC-Sign"));

            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(null, headers), String.class);

            log.info("法大大 Token 响应: status={}, body={}",
                    resp.getStatusCode().value(),
                    resp.getBody() != null ? resp.getBody().substring(0, Math.min(500, resp.getBody().length())) : "null");

            if (resp.getBody() != null) {
                Map<String, Object> raw = MAPPER.readValue(resp.getBody(), new TypeReference<>() {});
                if (raw != null && raw.get("data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> d = (Map<String, Object>) raw.get("data");
                    cachedToken = String.valueOf(d.get("accessToken"));
                    tokenExpireAt = System.currentTimeMillis() + 7000L * 1000;
                    log.info("accessToken 获取成功, token前8位={}", cachedToken.length() > 8 ? cachedToken.substring(0, 8) + "..." : "***");
                    return cachedToken;
                }
                String code = raw != null ? String.valueOf(raw.getOrDefault("code", "无")) : "null";
                String msg = raw != null ? String.valueOf(raw.getOrDefault("msg", "无")) : "null";
                log.error("Token响应异常: code={}, msg={}, 完整响应={}", code, msg, resp.getBody());
            } else {
                log.error("Token响应 body 为空, status={}", resp.getStatusCode().value());
            }
            throw new RuntimeException("Token获取失败: 响应体无有效 accessToken");
        } catch (RuntimeException e) {
            log.error("法大大认证失败: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("法大大认证失败(网络/IO异常): type={}, message={}", e.getClass().getName(), e.getMessage(), e);
            throw new RuntimeException("法大大认证失败: " + e.getMessage(), e);
        }
    }

    /** 步骤1: 获取上传URL → PUT文件 → 步骤2: 文件处理获取fileId */
    private String uploadFile(String token, Long localFileId) {
        try {
            // 1.1 获取上传URL (fileType=doc 表示签署文档)
            Map<String, Object> r1 = call(token, "/file/get-upload-url", Map.of("fileType", "doc"));
            if (r1 == null || r1.get("uploadUrl") == null) {
                log.error("获取uploadUrl失败: {}", toJson(r1));
                return null;
            }
            String uploadUrl = String.valueOf(r1.get("uploadUrl"));
            String fddFileUrl = String.valueOf(r1.get("fddFileUrl"));

            // 1.2 PUT 上传文件（使用原生HttpURLConnection，法大大URL自带鉴权签名）
            LocalFile localFile = readLocalFile(localFileId);
            byte[] bytes = localFile.bytes();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(uploadUrl).openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.getOutputStream().write(bytes);
            conn.getOutputStream().flush();
            conn.getOutputStream().close();
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            if (responseCode != 200) {
                log.error("PUT上传失败: HTTP {}", responseCode);
                return null;
            }

            // 1.3 文件处理获取 fileId
            Map<String, Object> r2 = call(token, "/file/process", Map.of("fddFileUrlList", List.of(
                    Map.of("fileType", "doc", "fddFileUrl", fddFileUrl,
                            "fileName", localFile.fileName()))));
            if (r2 != null && r2.get("fileIdList") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) r2.get("fileIdList");
                if (!list.isEmpty()) {
                    String fileId = String.valueOf(list.get(0).get("fileId"));
                    log.info("文件上传处理成功: fileId={}", fileId);
                    return fileId;
                }
            }
            log.error("文件处理失败: {}", toJson(r2));
            return null;
        } catch (Exception e) {
            log.error("上传文件异常", e);
            return null;
        }
    }

    /** 步骤3: 创建签署任务 */
    private String createSignTask(String token, String fddFileId, SignatureRequest req) {
        // 发起方
        Map<String, Object> initiator = Map.of(
                "idType", "corp",
                "openId", props.fadadaCorpId());

        // 文档列表
        List<Map<String, Object>> docs = List.of(Map.of(
                "docId", "doc-0",
                "docName", "合同#" + (req.contractId() != null ? req.contractId() : "签署"),
                "docFileId", fddFileId));

        // 参与方: 发起方企业 + 对方个人（每个actor需嵌套在"actor"子对象中）
        List<Map<String, Object>> actors = new ArrayList<>();

        Map<String, Object> corpInner = new LinkedHashMap<>();
        corpInner.put("actorId", "发起方");
        corpInner.put("actorType", "corp");
        corpInner.put("actorName", "本公司");
        corpInner.put("permissions", List.of("sign"));
        corpInner.put("actorOpenId", props.fadadaCorpId());
        actors.add(Map.of("actor", corpInner));

        if (req.signerName() != null && !req.signerName().isBlank()) {
            Map<String, Object> personInner = new LinkedHashMap<>();
            personInner.put("actorId", "对方");
            personInner.put("actorType", "person");
            personInner.put("actorName", req.signerName());
            personInner.put("permissions", List.of("sign"));
            personInner.put("accountName", req.signerMobile() != null ? req.signerMobile() : "");
            actors.add(Map.of("actor", personInner));
        }

        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("initiator", initiator);
        biz.put("signTaskSubject", "合同签署#" + (req.contractId() != null ? req.contractId() : ""));
        biz.put("signDocType", "contract");
        biz.put("autoStart", false);
        biz.put("autoFillFinalize", true);
        if (props.resolvedCallbackUrl() != null && !props.resolvedCallbackUrl().isBlank()) {
            biz.put("callbackUrl", props.resolvedCallbackUrl());
        }
        biz.put("docs", docs);
        biz.put("actors", actors);

        Map<String, Object> r = call(token, "/sign-task/create", biz);
        if (r != null && r.get("signTaskId") != null) {
            String tid = String.valueOf(r.get("signTaskId"));
            log.info("签署任务创建成功: signTaskId={}", tid);
            return tid;
        }
        log.error("创建签署任务失败: {}", toJson(r));
        return null;
    }

    /** 步骤4: 提交签署任务 */
    private boolean startSignTask(String token, String signTaskId) {
        Map<String, Object> r = call(token, "/sign-task/start", Map.of("signTaskId", signTaskId));
        return r != null;
    }

    /** 步骤5: 获取参与方签署链接（取对方个人参与方的签署链接） */
    private String getActorSignUrl(String token, String signTaskId) {
        Map<String, Object> r = call(token, "/sign-task/actor/get-url",
                Map.of("signTaskId", signTaskId, "actorId", "对方"));
        if (r != null) {
            // 法大大返回的字段名是 actorSignTaskUrl / actorSignTaskEmbedUrl
            Object url = r.get("actorSignTaskUrl");
            if (url == null) url = r.get("actorSignTaskEmbedUrl");
            if (url != null) return String.valueOf(url);
        }
        log.warn("未获取到签署链接");
        return null;
    }

    /** 查询任务详情 */
    private String getDownloadUrl(String token, String signTaskId) {
        Map<String, Object> r = call(token, "/sign-task/owner/get-download-url",
                Map.of("signTaskId", signTaskId));
        if (r != null) {
            Object url = r.get("downloadUrl");
            if (url == null) url = r.get("url");
            if (url != null) return String.valueOf(url);
        }
        return null;
    }

    // ==================== 通用 API 调用 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String token, String path, Map<String, Object> bizParams) {
        try {
            Map<String, String> headerParams = new LinkedHashMap<>();
            headerParams.put("X-FASC-App-Id", props.fadadaAppId());
            headerParams.put("X-FASC-Sign-Type", SIGN_TYPE);
            headerParams.put("X-FASC-Timestamp", String.valueOf(System.currentTimeMillis()));
            headerParams.put("X-FASC-Nonce", UUID.randomUUID().toString().replace("-", ""));
            headerParams.put("X-FASC-AccessToken", token);
            headerParams.put("X-FASC-Api-SubVersion", SUB_VERSION);
            String bizJson = MAPPER.writeValueAsString(bizParams);
            headerParams.put("bizContent", bizJson);

            HttpHeaders headers = buildHeaders(headerParams);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String formBody = "bizContent=" + URLEncoder.encode(bizJson, StandardCharsets.UTF_8);
            String url = props.fadadaApiUrl() + path;
            log.debug("Fadada call: {} biz={}", path, bizJson.substring(0, Math.min(100, bizJson.length())));
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(formBody, headers), String.class);

            if (resp.getBody() != null) {
                Map<String, Object> raw = MAPPER.readValue(resp.getBody(), new TypeReference<>() {});
                String code = String.valueOf(raw.getOrDefault("code", ""));
                String msg = String.valueOf(raw.getOrDefault("msg", ""));
                if (!"100000".equals(code)) {
                    log.warn("API {} 返回失败: code={} msg={}", path, code, msg);
                }
                if (raw.get("data") instanceof Map) return (Map<String, Object>) raw.get("data");
                return raw;
            }
            return null;
        } catch (Exception e) {
            log.error("API {} 异常: {}", path, e.getMessage());
            return null;
        }
    }

    private HttpHeaders buildHeaders(Map<String, String> params) {
        HttpHeaders h = new HttpHeaders();
        params.forEach(h::set);
        String sorted = params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        String sig = computeSign(sorted, params.get("X-FASC-Timestamp"), props.fadadaAppSecret());
        h.set("X-FASC-Sign", sig);
        return h;
    }

    private String computeSign(String sorted, String ts, String secret) {
        try {
            String signText = sha256Hex(sorted);
            byte[] secretSigning = hmac(secret.getBytes(StandardCharsets.UTF_8),
                    ts.getBytes(StandardCharsets.UTF_8));
            byte[] sig = hmac(secretSigning, signText.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(sig).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("签名失败", e);
        }
    }

    // ==================== 工具 ====================

    private LocalFile readLocalFile(Long fileId) {
        try {
            var info = fileInfoMapper.selectById(fileId);
            if (info == null) {
                throw new IllegalStateException("文件不存在: fileId=" + fileId);
            }
            var path = fileStorageService.resolve(info.getObjectKey());
            byte[] bytes = Files.readAllBytes(path);
            log.info("读取签章文件成功: fileId={}, fileName={}, objectKey={}, path={}, size={}",
                    fileId, info.getFileName(), info.getObjectKey(), path, bytes.length);
            return new LocalFile(resolveFileName(info.getFileName(), fileId), bytes);
        } catch (Exception e) {
            log.error("读取签章文件失败: fileId={}", fileId, e);
            throw new IllegalStateException("读取签章文件失败: fileId=" + fileId, e);
        }
    }

    private String resolveFileName(String fileName, Long fileId) {
        return fileName != null && !fileName.isBlank() ? fileName : "contract-" + fileId + ".pdf";
    }

    private record LocalFile(String fileName, byte[] bytes) {}

    private String mapStatus(String s) {
        if (s == null) return "UNKNOWN";
        return switch (s.toUpperCase()) {
            case "SIGN_COMPLETED", "TASK_FINISHED", "COMPLETED", "SIGNED" -> "SIGNED";
            case "SIGN_PROGRESS" -> "SIGNING";
            case "TASK_CREATED", "FILL_PROGRESS", "FILL_COMPLETED" -> "PENDING";
            default -> s.toUpperCase();
        };
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte v : b) sb.append(String.format("%02x", v));
        return sb.toString();
    }

    private String toJson(Object o) {
        try { return MAPPER.writeValueAsString(o); } catch (Exception ignored) { return String.valueOf(o); }
    }

    private SignatureResponse err(String msg) {
        return new SignatureResponse(null, "ERROR", null, null, msg);
    }
}
