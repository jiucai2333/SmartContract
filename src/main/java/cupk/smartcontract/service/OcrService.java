package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.OcrProperties;
import cupk.smartcontract.dto.OcrExtractResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final int MAX_TEXT_CHARS = 50000;
    private static final int POLL_INTERVAL_MS = 5000;
    private static final int MAX_POLL_TIME_SECONDS = 120;

    private final OcrProperties ocrProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OcrService(OcrProperties ocrProperties, ObjectMapper objectMapper) {
        this.ocrProperties = ocrProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public record OcrProcessResult(String rawText, int pageCount, OcrExtractResult extract) {}

    /**
     * 主入口：根据 provider 调用不同的 OCR 服务
     */
    public OcrProcessResult process(Path filePath, String fileType) throws IOException {
        String normalized = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        String provider = ocrProperties.resolvedProvider();

        if (!"pdf".equals(normalized) && !"docx".equals(normalized)) {
            throw new IllegalArgumentException("仅支持 PDF 或 DOCX 格式");
        }

        String rawText;
        int pageCount;

        if ("docx".equals(normalized)) {
            rawText = extractDocxText(filePath);
            pageCount = 1;
        } else if ("paddle".equals(provider)) {
            // PaddleOCR 云服务，原生支持 PDF，直接传整个文件
            PaddleResult result = callPaddleOcr(filePath);
            rawText = result.text;
            pageCount = result.pages;
        } else if ("aliyun-openapi".equals(provider)) {
            // 阿里云 OpenAPI
            rawText = callAliyunOpenApi(filePath);
            pageCount = 1;
        } else {
            // 阿里云云市场 APPCODE（旧）
            rawText = callAliyunOcrApi(filePath);
            pageCount = 1;
        }

        if (rawText == null || rawText.isBlank()) {
            throw new IllegalStateException("未能从文件中识别出文字，请上传更清晰的扫描件");
        }

        String clipped = clip(rawText, MAX_TEXT_CHARS);
        return new OcrProcessResult(clipped, pageCount, null);
    }

    // ==================== PaddleOCR 云服务 ====================

    private record PaddleResult(String text, int pages) {}

    /**
     * 调用 PaddleOCR 云服务（paddleocr.aistudio-app.com）
     * 流程：提交任务 → 轮询等待 → 下载 JSONL 结果 → 提取 markdown 文本
     */
    private PaddleResult callPaddleOcr(Path filePath) throws IOException {
        String token = ocrProperties.resolvedPaddleToken();
        if (token.isBlank()) {
            throw new IllegalStateException("缺少 PaddleOCR Token，请配置 ai.ocr.paddle-token");
        }

        String jobUrl = ocrProperties.resolvedPaddleJobUrl();
        String authHeader = "bearer " + token;
        byte[] fileBytes = Files.readAllBytes(filePath);

        // Step 1: 提交 OCR 任务（multipart 上传文件）
        log.info("[PaddleOCR] Submitting job, file size: {} bytes", fileBytes.length);

        String boundary = "----PaddleOcr" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(jobUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", authHeader)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(boundary, fileBytes, filePath)))
                .build();

        HttpResponse<String> submitResp;
        try {
            submitResp = send(submitReq);
        } catch (Exception e) {
            throw new IOException("PaddleOCR 任务提交失败: " + e.getMessage(), e);
        }

        if (submitResp.statusCode() != 200) {
            throw new IOException("PaddleOCR 任务提交失败，HTTP " + submitResp.statusCode() + ": " + submitResp.body());
        }

        JsonNode submitJson = objectMapper.readTree(submitResp.body());
        String jobId = submitJson.path("data").path("jobId").asText();
        if (jobId.isBlank()) {
            throw new IOException("PaddleOCR 未返回 jobId: " + submitResp.body());
        }
        log.info("[PaddleOCR] Job submitted, jobId: {}", jobId);

        // Step 2: 轮询等待结果
        String jsonlUrl = pollUntilDone(jobUrl, jobId, authHeader);

        // Step 3: 下载 JSONL 结果
        log.info("[PaddleOCR] Downloading result from: {}", jsonlUrl);
        HttpRequest resultReq = HttpRequest.newBuilder()
                .uri(URI.create(jsonlUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> resultResp = send(resultReq);

        // Step 4: 解析 JSONL，提取 markdown 文本
        String[] lines = resultResp.body().split("\n");
        StringBuilder allText = new StringBuilder();
        int pageNum = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;
            try {
                JsonNode lineJson = objectMapper.readTree(line);
                JsonNode layoutResults = lineJson.path("result").path("layoutParsingResults");
                if (layoutResults.isArray()) {
                    for (JsonNode res : layoutResults) {
                        pageNum++;
                        String mdText = res.path("markdown").path("text").asText("");
                        if (!mdText.isBlank()) {
                            if (!allText.isEmpty()) allText.append("\n\f\n");
                            allText.append(mdText);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[PaddleOCR] Failed to parse JSONL line: {}", e.getMessage());
            }
        }

        String text = allText.toString().trim();
        log.info("[PaddleOCR] Done. pages: {}, text length: {}", pageNum, text.length());
        return new PaddleResult(text, pageNum > 0 ? pageNum : 1);
    }

    private String extractDocxText(Path filePath) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(filePath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return xml.replace("</w:p>", "\n")
                            .replace("</w:tr>", "\n")
                            .replace("</w:tc>", "\t")
                            .replaceAll("<[^>]+>", "")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .replace("&quot;", "\"")
                            .replace("&apos;", "'")
                            .replaceAll("\\n{3,}", "\n\n")
                            .trim();
                }
            }
        }
        throw new IOException("DOCX 文件缺少正文内容");
    }

    private String pollUntilDone(String jobUrl, String jobId, String authHeader) throws IOException {
        long start = System.currentTimeMillis();
        long maxWait = (long) ocrProperties.resolvedPaddleTimeoutSeconds() * 1000;

        while (true) {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > maxWait) {
                throw new IOException("PaddleOCR 任务超时（" + ocrProperties.resolvedPaddleTimeoutSeconds() + "s），jobId: " + jobId);
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("轮询被中断", e);
            }

            HttpRequest pollReq = HttpRequest.newBuilder()
                    .uri(URI.create(jobUrl + "/" + jobId))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", authHeader)
                    .GET()
                    .build();

            HttpResponse<String> pollResp = send(pollReq);
            if (pollResp.statusCode() != 200) {
                throw new IOException("PaddleOCR 查询任务失败，HTTP " + pollResp.statusCode());
            }

            JsonNode pollJson = objectMapper.readTree(pollResp.body());
            String state = pollJson.path("data").path("state").asText("unknown");
            log.info("[PaddleOCR] Job state: {}", state);

            switch (state) {
                case "done" -> {
                    String jsonUrl = pollJson.path("data").path("resultUrl").path("jsonUrl").asText();
                    if (jsonUrl.isBlank()) {
                        throw new IOException("PaddleOCR 任务完成但未返回结果 URL");
                    }
                    return jsonUrl;
                }
                case "failed" -> {
                    String error = pollJson.path("data").path("errorMsg").asText("未知错误");
                    throw new IOException("PaddleOCR 任务失败: " + error);
                }
                case "pending", "running" -> {
                    // 继续轮询
                }
                default -> throw new IOException("PaddleOCR 未知状态: " + state);
            }
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] fileBytes, Path filePath) throws IOException {
        String filename = filePath.getFileName().toString();
        String model = "PaddleOCR-VL-1.6";
        String optionalPayload = "{\"useDocOrientationClassify\":false,\"useDocUnwarping\":false,\"useChartRecognition\":false}";

        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        sb.append(model).append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"optionalPayload\"\r\n\r\n");
        sb.append(optionalPayload).append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");

        byte[] header = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[header.length + fileBytes.length + footer.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(fileBytes, 0, body, header.length, fileBytes.length);
        System.arraycopy(footer, 0, body, header.length + fileBytes.length, footer.length);

        return body;
    }

    // ==================== 阿里云 OpenAPI（保留，备用） ====================

    private String callAliyunOpenApi(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        String base64Image = Base64.getEncoder().encodeToString(bytes);

        java.util.Map<String, String> params = new java.util.TreeMap<>();
        params.put("AccessKeyId", ocrProperties.accessKeyId());
        params.put("Action", "RecognizeAllText");
        params.put("Format", "JSON");
        params.put("OutputBarCode", "false");
        params.put("OutputCharInfo", "false");
        params.put("OutputFigure", "false");
        params.put("OutputOricoord", "false");
        params.put("OutputQrcode", "false");
        params.put("OutputStamp", "false");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("Type", "Advanced");
        params.put("Version", "2021-07-07");
        params.put("Timestamp", utcTimestamp());
        params.put("body", base64Image);

        String signature = aliyunSign(params, ocrProperties.accessKeySecret(), "POST");
        params.put("Signature", signature);

        String formBody = aliyunBuildQuery(params);
        String url = "https://" + ocrProperties.resolvedOpenapiEndpoint() + "/";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(ocrProperties.resolvedTimeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() >= 300) {
            throw new IOException("阿里云 OCR 调用失败，HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode code = root.get("Code");
        if (code != null && !code.isNull()) {
            throw new IOException("阿里云 OCR 错误: " + code.asText() + " - " + root.path("Message").asText(""));
        }
        return root.path("Data").path("content").asText("");
    }

    // ==================== 阿里云云市场 APPCODE（保留，备用） ====================

    private String callAliyunOcrApi(Path filePath) throws IOException {
        if (!ocrProperties.enabled() || ocrProperties.appCode() == null || ocrProperties.appCode().isBlank()) {
            throw new IllegalStateException("阿里云 OCR 未配置 APPCODE");
        }
        byte[] bytes = Files.readAllBytes(filePath);
        String base64Image = Base64.getEncoder().encodeToString(bytes);

        Map<String, Object> payload = Map.of("image", base64Image, "prob", true, "charDesc", true, "format", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ocrProperties.resolvedEndpoint()))
                .timeout(Duration.ofSeconds(ocrProperties.resolvedTimeoutSeconds()))
                .header("Authorization", "APPCODE " + ocrProperties.appCode())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() >= 300) {
            throw new IOException("阿里云 OCR 调用失败，HTTP " + resp.statusCode());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode wordsResult = root.get("words_result");
        if (wordsResult == null || wordsResult.isNull()) return "";
        StringBuilder text = new StringBuilder();
        if (wordsResult.isArray()) {
            for (JsonNode item : wordsResult) {
                JsonNode w = item.get("words");
                if (w != null) text.append(w.asText()).append("\n");
            }
        }
        return text.toString().trim();
    }

    // ==================== 阿里云签名工具 ====================

    private String aliyunSign(Map<String, String> params, String secret, String method) {
        try {
            String[] keys = params.keySet().toArray(new String[0]);
            StringBuilder canonical = new StringBuilder();
            for (String key : keys) {
                canonical.append("&").append(percentEncode(key)).append("=").append(percentEncode(params.get(key)));
            }
            String canonicalQuery = canonical.substring(1);
            String stringToSign = method + "&" + percentEncode("/") + "&" + percentEncode(canonicalQuery);

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec((secret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("签名失败", e);
        }
    }

    private String percentEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (Exception e) {
            throw new RuntimeException("编码失败", e);
        }
    }

    private String aliyunBuildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(percentEncode(e.getKey())).append("=").append(percentEncode(e.getValue()));
        }
        return sb.toString();
    }

    private String utcTimestamp() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }

    // ==================== 工具方法 ====================

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    private String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
