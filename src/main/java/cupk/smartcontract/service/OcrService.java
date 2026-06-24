package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.OcrProperties;
import cupk.smartcontract.dto.OcrDocumentVO;
import cupk.smartcontract.dto.OcrExtractVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final int MAX_TEXT_CHARS = 50000;
    private static final int POLL_INTERVAL_MS = 5000;

    private final OcrProperties ocrProperties;
    private final ObjectMapper objectMapper;
    private final LayoutFeatureService layoutFeatureService;
    private final HttpClient httpClient;

    public OcrService(OcrProperties ocrProperties, ObjectMapper objectMapper,
                      LayoutFeatureService layoutFeatureService) {
        this.ocrProperties = ocrProperties;
        this.objectMapper = objectMapper;
        this.layoutFeatureService = layoutFeatureService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public record OcrProcessResult(
            String rawText, String formattedHtml, int pageCount, OcrExtractVO extract,
            String rawJson, String parseJson, List<String> warnings,
            String source, String model, long durationMs, String legacyMarkdownHtml
    ) {}

    /**
     * 根据文件类型和配置的 provider 调用不同 OCR 处理逻辑。
     */
    public OcrProcessResult process(Path filePath, String fileType) throws IOException {
        long startedAt = System.currentTimeMillis();
        String normalized = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        String provider = ocrProperties.resolvedProvider();

        if (!List.of("pdf", "docx", "jpg", "jpeg", "png", "webp").contains(normalized)) {
            throw new IllegalArgumentException("仅支持 PDF、DOCX、JPG、JPEG、PNG 或 WEBP 文件");
        }

        String rawText;
        int pageCount;
        String rawJson = null;
        String parseJson = null;
        List<String> warnings = new ArrayList<>();
        String source;
        String model = null;

        if ("docx".equals(normalized)) {
            rawText = extractDocxText(filePath);
            pageCount = 1;
            source = "docx";
        } else if ("paddle".equals(provider)) {
            PaddleResult result = callPaddleOcr(filePath, normalized);
            rawText = result.text;
            pageCount = result.pages;
            rawJson = result.rawJson;
            parseJson = result.parseJson;
            warnings.addAll(result.warnings);
            source = "paddleocr";
            model = ocrProperties.resolvedPaddleModel();
        } else if ("aliyun-openapi".equals(provider)) {
            rawText = callAliyunOpenApi(filePath);
            pageCount = 1;
            source = "aliyun-openapi";
        } else {
            rawText = callAliyunOcrApi(filePath);
            pageCount = 1;
            source = "aliyun";
        }

        String legacyMarkdownHtml = "paddle".equals(provider) && rawJson != null
                ? legacyMarkdownHtml(rawJson) : null;
        if ((rawText == null || rawText.isBlank()) && !StringUtils.hasText(legacyMarkdownHtml)) {
            throw new IllegalStateException("OCR 未返回有效识别内容，请检查文件是否清晰或服务配置是否正确");
        }

        String clipped = clip(rawText == null ? "" : rawText, MAX_TEXT_CHARS);
        return new OcrProcessResult(clipped, ocrTextToHtml(clipped), pageCount, null,
                rawJson, parseJson, warnings, source, model, System.currentTimeMillis() - startedAt,
                legacyMarkdownHtml);
    }

    private record PaddleResult(
            String text, int pages, String rawJson, String parseJson, List<String> warnings
    ) {}

    /**
     * 调用 PaddleOCR 服务，提交文件后轮询任务结果，并解析返回的 JSONL/Markdown 文本。
     */
    private PaddleResult callPaddleOcr(Path filePath, String fileType) throws IOException {
        String token = ocrProperties.resolvedPaddleToken();
        if (token.isBlank()) {
            throw new IllegalStateException("未配置 PaddleOCR Token，请设置 ai.ocr.paddle-token");
        }

        String jobUrl = ocrProperties.resolvedPaddleJobUrl();
        String authHeader = "bearer " + token;
        byte[] fileBytes = Files.readAllBytes(filePath);

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
            throw new IOException("PaddleOCR 任务提交失败：" + e.getMessage(), e);
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

        String jsonlUrl = pollUntilDone(jobUrl, jobId, authHeader);

        log.info("[PaddleOCR] Downloading result from: {}", jsonlUrl);
        HttpRequest resultReq = HttpRequest.newBuilder()
                .uri(URI.create(jsonlUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> resultResp = send(resultReq);
        if (resultResp.statusCode() >= 300) {
            throw new IOException("PaddleOCR 结果下载失败，HTTP " + resultResp.statusCode());
        }

        String rawJson = resultResp.body();
        String[] lines = rawJson.split("\n");
        int pageNum = 0;
        List<OcrDocumentVO.Page> pages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        PageSize imagePageSize = imageFileType(fileType) ? readImagePageSize(filePath) : PageSize.EMPTY;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            line = line.trim();
            if (line.isBlank()) continue;
            try {
                JsonNode lineJson = objectMapper.readTree(line);
                JsonNode layoutResults = lineJson.path("result").path("layoutParsingResults");
                if (layoutResults.isArray()) {
                    for (JsonNode res : layoutResults) {
                        pageNum++;
                        pages.add(toPage(res, pageNum, imagePageSize, warnings));
                    }
                }
            } catch (Exception e) {
                log.warn("[PaddleOCR] Failed to parse JSONL line: {}", e.getMessage());
                warnings.add("JSONL line " + (lineIndex + 1) + " parse failed: " + briefMessage(e));
            }
        }

        if (pages.isEmpty()) {
            warnings.add("Paddle block 数据缺失，未生成结构化 block，已进入纯文本/legacy 降级。");
        }
        String text = blocksToText(pages);
        OcrDocumentVO document = new OcrDocumentVO(
                imageFileType(fileType) ? "image" : fileType,
                "paddleocr",
                true,
                pages,
                null,
                warnings
        );
        document = layoutFeatureService.generate(document, warnings);
        String parseJson = objectMapper.writeValueAsString(document);
        log.info("[PaddleOCR] Done. pages: {}, text length: {}", pageNum, text.length());
        warnings.forEach(warning -> log.warn("[PaddleOCR] {}", warning));
        return new PaddleResult(text, pageNum > 0 ? pageNum : 1, rawJson, parseJson, warnings);
    }

    private OcrDocumentVO.Page toPage(JsonNode result, int pageNo, PageSize fallbackPageSize,
                                      List<String> warnings) {
        PageSize pageSize = readPageSize(result);
        Integer width = pageSize.width() != null ? pageSize.width() : fallbackPageSize.width();
        Integer height = pageSize.height() != null ? pageSize.height() : fallbackPageSize.height();
        List<OcrDocumentVO.Block> blocks = new ArrayList<>();
        Map<String, Integer> bboxSources = new LinkedHashMap<>();
        int bboxMissingCount = 0;
        int bboxInvalidCount = 0;
        JsonNode candidates = firstArray(result, "parsing_res_list", "blocks", "layoutResults", "layout_results");

        if (candidates != null) {
            int order = 1;
            for (JsonNode candidate : candidates) {
                String text = firstText(candidate, "block_content", "text", "content");
                String type = normalizeBlockType(firstText(candidate,
                        "block_label", "region_type", "layout_type", "type", "label"));
                BboxResult bboxResult = readBbox(candidate);
                if (bboxResult.bbox().isEmpty()) {
                    if (bboxResult.invalid()) bboxInvalidCount++;
                    else bboxMissingCount++;
                } else {
                    bboxSources.merge(bboxResult.source(), 1, Integer::sum);
                }
                Double confidence = firstDouble(candidate, "score", "confidence", "prob",
                        "probability", "rec_score", "det_score", "cls_score");
                blocks.add(new OcrDocumentVO.Block(
                        "p" + pageNo + "_b" + order,
                        type,
                        text,
                        bboxResult.bbox(),
                        bboxResult.bbox().isEmpty() ? null : bboxResult.source(),
                        inferAlign(candidate),
                        "unknown",
                        confidence,
                        order++,
                        "paddleocr",
                        true,
                        "table".equals(type) ? candidate : null,
                        null
                ));
            }
        }

        if (blocks.isEmpty()) {
            warnings.add("Page " + pageNo
                    + " Paddle block 数据缺失，未生成结构化 block，已进入纯文本/legacy 降级。");
        }
        if (blocks.stream().allMatch(block -> block.bbox() == null || block.bbox().isEmpty())) {
            warnings.add("Page " + pageNo + " has no bbox data.");
        }
        bboxSources.forEach((source, count) ->
                warnings.add("Page " + pageNo + " bbox_source=" + source + ", count=" + count));
        if (bboxMissingCount > 0) {
            warnings.add("Page " + pageNo + " bbox_missing_count=" + bboxMissingCount);
        }
        if (bboxInvalidCount > 0) {
            warnings.add("Page " + pageNo + " bbox_parse_failed_count=" + bboxInvalidCount);
        }
        if (width == null || height == null) {
            warnings.add("Page " + pageNo + " page_size_unavailable");
        } else if (pageSize.source() != null) {
            warnings.add("Page " + pageNo + " page_size_source=" + pageSize.source());
        }
        if (blocks.stream().allMatch(block -> block.confidence() == null)) {
            warnings.add("Page " + pageNo + " has no confidence data.");
        }
        return new OcrDocumentVO.Page(pageNo, width, height, blocks);
    }

    private String blocksToText(List<OcrDocumentVO.Page> pages) {
        StringBuilder text = new StringBuilder();
        for (OcrDocumentVO.Page page : pages) {
            if (page.blocks() == null) continue;
            page.blocks().stream()
                    .sorted(java.util.Comparator.comparingInt(OcrDocumentVO.Block::order))
                    .map(OcrDocumentVO.Block::text)
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(value -> {
                        if (!text.isEmpty()) text.append('\n');
                        text.append(value.trim());
                    });
        }
        return text.toString();
    }

    private JsonNode firstArray(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = findField(node, name);
            if (value != null && value.isArray()) return value;
        }
        return null;
    }

    private JsonNode findField(JsonNode node, String name) {
        if (node == null || node.isValueNode()) return null;
        JsonNode direct = node.get(name);
        if (direct != null) return direct;
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                JsonNode found = findField(fields.next().getValue(), name);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findField(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode()) return value.asText("");
        }
        return "";
    }

    private Integer firstInteger(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = findField(node, name);
            if (value != null && value.canConvertToInt()) return value.asInt();
        }
        return null;
    }

    private Double firstDouble(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isNumber()) return value.asDouble();
        }
        return null;
    }

    private BboxResult readBbox(JsonNode node) {
        String invalidSource = null;
        for (String name : List.of("block_bbox", "bbox", "box", "boxes", "coordinate",
                "coordinates", "rect", "points", "poly", "polygon", "text_region",
                "layout_bbox")) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) continue;
            List<Double> bbox = normalizeBbox(value);
            if (!bbox.isEmpty()) {
                return new BboxResult(bbox, name, false);
            }
            if (invalidSource == null) invalidSource = name;
        }
        return new BboxResult(List.of(), invalidSource == null ? "none" : invalidSource,
                invalidSource != null);
    }

    private List<Double> normalizeBbox(JsonNode value) {
        if (value.isObject()) {
            List<Double> edges = objectBbox(value);
            if (!edges.isEmpty()) return edges;
        }
        List<Double> values = new ArrayList<>();
        flattenNumbers(value, values);
        if (values.size() == 4) {
            double x0 = values.get(0);
            double y0 = values.get(1);
            double x1 = values.get(2);
            double y1 = values.get(3);
            if (x1 < x0 || y1 < y0) return List.of();
            return List.of(x0, y0, x1, y1);
        }
        if (values.size() == 8) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < values.size(); i += 2) {
                minX = Math.min(minX, values.get(i));
                minY = Math.min(minY, values.get(i + 1));
                maxX = Math.max(maxX, values.get(i));
                maxY = Math.max(maxY, values.get(i + 1));
            }
            return List.of(minX, minY, maxX, maxY);
        }
        return List.of();
    }

    private List<Double> objectBbox(JsonNode value) {
        Double left = firstNumber(value, "left");
        Double top = firstNumber(value, "top");
        Double right = firstNumber(value, "right");
        Double bottom = firstNumber(value, "bottom");
        if (left != null && top != null && right != null && bottom != null
                && right >= left && bottom >= top) {
            return List.of(left, top, right, bottom);
        }
        Double x = firstNumber(value, "x");
        Double y = firstNumber(value, "y");
        Double width = firstNumber(value, "width", "w");
        Double height = firstNumber(value, "height", "h");
        if (x != null && y != null && width != null && height != null
                && width >= 0 && height >= 0) {
            return List.of(x, y, x + width, y + height);
        }
        return List.of();
    }

    private Double firstNumber(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isNumber()) return value.asDouble();
        }
        return null;
    }

    private void flattenNumbers(JsonNode node, List<Double> values) {
        if (node.isNumber()) {
            values.add(node.asDouble());
        } else if (node.isArray()) {
            node.forEach(child -> flattenNumbers(child, values));
        }
    }

    private PageSize readPageSize(JsonNode result) {
        JsonNode prunedResult = result == null ? null : result.get("prunedResult");
        Integer width = directInteger(prunedResult, "width");
        Integer height = directInteger(prunedResult, "height");
        String source = width != null || height != null ? "prunedResult.width_height" : null;

        if (width == null) {
            width = directInteger(result, "width", "pageWidth", "image_width", "img_width");
        }
        if (height == null) {
            height = directInteger(result, "height", "pageHeight", "image_height", "img_height");
        }
        if (source == null && (width != null || height != null)) source = "layout_result";

        JsonNode inputImage = result == null ? null : result.get("input_img");
        if (inputImage != null && inputImage.isObject()) {
            if (width == null) width = directInteger(inputImage, "width", "image_width", "img_width");
            if (height == null) height = directInteger(inputImage, "height", "image_height", "img_height");
            if (source == null && (width != null || height != null)) source = "input_img";
        }

        JsonNode shape = result == null ? null : result.get("shape");
        if ((width == null || height == null) && shape != null && shape.isArray()) {
            List<Double> values = new ArrayList<>();
            flattenNumbers(shape, values);
            if (values.size() >= 2) {
                if (height == null) height = positiveInteger(values.get(0));
                if (width == null) width = positiveInteger(values.get(1));
                if (source == null && (width != null || height != null)) source = "shape";
            }
        }
        return new PageSize(width, height, source);
    }

    private Integer directInteger(JsonNode node, String... names) {
        if (node == null || !node.isObject()) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            Integer parsed = positiveInteger(value);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private Integer positiveInteger(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isNumber()) return positiveInteger(value.asDouble());
        if (value.isTextual()) {
            try {
                return positiveInteger(Double.parseDouble(value.asText().trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer positiveInteger(Double value) {
        if (value == null || !Double.isFinite(value) || value <= 0 || value > Integer.MAX_VALUE) return null;
        return value.intValue();
    }

    private PageSize readImagePageSize(Path filePath) {
        try {
            var image = ImageIO.read(filePath.toFile());
            return image == null ? PageSize.EMPTY
                    : new PageSize(image.getWidth(), image.getHeight(), "image_file");
        } catch (Exception ex) {
            log.warn("[PaddleOCR] Image size could not be read: {}", briefMessage(ex));
            return PageSize.EMPTY;
        }
    }

    private String briefMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() <= 160 ? message : message.substring(0, 160);
    }

    private record BboxResult(List<Double> bbox, String source, boolean invalid) {
    }

    private record PageSize(Integer width, Integer height, String source) {
        private static final PageSize EMPTY = new PageSize(null, null, null);
    }

    private String normalizeBlockType(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (value.contains("title")) return "title";
        if (value.contains("header") || value.contains("heading")) return "heading";
        if (value.contains("table")) return "table";
        if (value.contains("footer")) return "footer";
        if (value.contains("page_number") || value.contains("page number")) return "page_number";
        if (value.contains("text") || value.contains("paragraph")) return "paragraph";
        return "unknown";
    }

    private String inferAlign(JsonNode node) {
        String align = firstText(node, "align", "alignment").toLowerCase(Locale.ROOT);
        return List.of("left", "center", "right").contains(align) ? align : "unknown";
    }

    private boolean imageFileType(String fileType) {
        return List.of("jpg", "jpeg", "png", "webp").contains(fileType);
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
        throw new IOException("DOCX 文件中未找到 word/document.xml");
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
                throw new IOException("PaddleOCR 轮询失败，HTTP " + pollResp.statusCode());
            }

            JsonNode pollJson = objectMapper.readTree(pollResp.body());
            String state = pollJson.path("data").path("state").asText("unknown");
            log.info("[PaddleOCR] Job state: {}", state);

            switch (state) {
                case "done" -> {
                    String jsonUrl = pollJson.path("data").path("resultUrl").path("jsonUrl").asText();
                    if (jsonUrl.isBlank()) {
                        throw new IOException("PaddleOCR 完成但未返回结果 URL");
                    }
                    return jsonUrl;
                }
                case "failed" -> {
                    String error = pollJson.path("data").path("errorMsg").asText("未知错误");
                    throw new IOException("PaddleOCR 任务失败: " + error);
                }
                case "pending", "running" -> {
                    // 继续等待。
                }
                default -> throw new IOException("PaddleOCR 返回未知状态: " + state);
            }
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] fileBytes, Path filePath) {
        String filename = filePath.getFileName().toString();
        String model = ocrProperties.resolvedPaddleModel();
        String optionalPayload;
        try {
            optionalPayload = objectMapper.writeValueAsString(Map.of(
                    "useDocOrientationClassify", ocrProperties.useDocOrientationClassify(),
                    "useDocUnwarping", ocrProperties.useDocUnwarping(),
                    "useChartRecognition", ocrProperties.useChartRecognition()
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build PaddleOCR optional payload", ex);
        }

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
            throw new IOException("阿里云 OCR 请求失败，HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode code = root.get("Code");
        if (code != null && !code.isNull()) {
            throw new IOException("阿里云 OCR 返回错误 " + code.asText() + " - " + root.path("Message").asText(""));
        }
        return root.path("Data").path("content").asText("");
    }

    private String callAliyunOcrApi(Path filePath) throws IOException {
        if (!ocrProperties.enabled() || ocrProperties.appCode() == null || ocrProperties.appCode().isBlank()) {
            throw new IllegalStateException("未配置阿里云 OCR APPCODE");
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
            throw new IOException("阿里云 OCR 请求失败，HTTP " + resp.statusCode());
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
            throw new RuntimeException("阿里云签名失败", e);
        }
    }

    private String percentEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (Exception e) {
            throw new RuntimeException("URL 编码失败", e);
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

    private String legacyMarkdownHtml(String rawJson) {
        StringBuilder markdown = new StringBuilder();
        for (String line : rawJson.split("\n")) {
            if (line.isBlank()) continue;
            try {
                JsonNode layoutResults = objectMapper.readTree(line)
                        .path("result").path("layoutParsingResults");
                if (!layoutResults.isArray()) continue;
                for (JsonNode result : layoutResults) {
                    String value = result.path("markdown").path("text").asText("");
                    if (!value.isBlank()) {
                        if (!markdown.isEmpty()) markdown.append("\n\f\n");
                        markdown.append(value);
                    }
                }
            } catch (Exception ex) {
                log.warn("[PaddleOCR] Legacy markdown extraction failed: {}", ex.getMessage());
            }
        }
        return markdown.isEmpty() ? null : ocrTextToHtml(markdown.toString());
    }

    /**
     * Legacy fallback for historical Paddle markdown responses.
     * Normal OCR import must build editor HTML from ocr_blocks_json blocks.
     */
    private String ocrTextToHtml(String text) {
        StringBuilder html = new StringBuilder();
        String[] lines = text.replace("\r", "").replace("\f", "\n").split("\n", -1);
        boolean previousBlank = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.matches("^[-| :]+$")) {
                previousBlank = true;
                continue;
            }
            String value = org.jsoup.nodes.Entities.escape(trimmed.replaceFirst("^#{1,6}\\s*", ""))
                    .replace("  ", "&nbsp;&nbsp;");
            if (trimmed.startsWith("# ")) {
                html.append("<h1 style=\"text-align:center\">").append(value).append("</h1>");
                previousBlank = false;
                continue;
            }
            if (trimmed.matches("^#{2,6}\\s+.*")) {
                html.append("<h2 style=\"text-align:left\">").append(value).append("</h2>");
                previousBlank = false;
                continue;
            }
            int leading = line.length() - line.stripLeading().length();
            String align = leading >= 4 ? "right" : leading >= 2 ? "center" : "left";
            String margin = previousBlank ? "8px 0" : "4px 0";
            html.append("<p style=\"text-align:").append(align)
                    .append(";margin:").append(margin).append(";line-height:1.8\">")
                    .append(value).append("</p>");
            previousBlank = false;
        }
        return html.toString();
    }
}
