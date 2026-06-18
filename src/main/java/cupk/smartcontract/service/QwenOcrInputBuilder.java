package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class QwenOcrInputBuilder {
    public static final int DEFAULT_MAX_CHARS = 30000;

    private final ObjectMapper objectMapper;

    public QwenOcrInputBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BuildResult build(String ocrBlocksJson) {
        return build(ocrBlocksJson, DEFAULT_MAX_CHARS);
    }

    BuildResult build(String ocrBlocksJson, int maxChars) {
        if (!StringUtils.hasText(ocrBlocksJson)) {
            throw new IllegalArgumentException("OCR parse JSON is empty");
        }
        if (maxChars <= 0) {
            throw new IllegalArgumentException("Qwen input max chars must be positive");
        }
        try {
            JsonNode root = objectMapper.readTree(ocrBlocksJson);
            JsonNode sourcePages = root.path("pages");
            if (!sourcePages.isArray()) {
                throw new IllegalArgumentException("OCR parse JSON does not contain pages");
            }

            ObjectNode compactRoot = objectMapper.createObjectNode();
            ArrayNode compactPages = compactRoot.putArray("pages");
            int totalBlocks = countBlocks(sourcePages);
            int includedBlocks = 0;
            boolean truncated = false;

            outer:
            for (JsonNode sourcePage : sourcePages) {
                ObjectNode compactPage = compactPage(sourcePage);
                ArrayNode compactBlocks = compactPage.putArray("blocks");
                boolean pageAdded = false;
                JsonNode sourceBlocks = sourcePage.path("blocks");
                if (!sourceBlocks.isArray()) continue;

                for (JsonNode sourceBlock : sourceBlocks) {
                    ObjectNode compactBlock = compactBlock(sourceBlock, sourcePage.path("page_no").asInt());
                    compactBlocks.add(compactBlock);
                    if (!pageAdded) {
                        compactPages.add(compactPage);
                        pageAdded = true;
                    }
                    String candidate = objectMapper.writeValueAsString(compactRoot);
                    if (candidate.length() > maxChars) {
                        compactBlocks.remove(compactBlocks.size() - 1);
                        if (compactBlocks.isEmpty()) {
                            compactPages.remove(compactPages.size() - 1);
                        }
                        truncated = true;
                        break outer;
                    }
                    includedBlocks++;
                }
            }

            compactRoot.put("block_count", includedBlocks);
            String json = objectMapper.writeValueAsString(compactRoot);
            if (json.length() > maxChars) {
                json = removeBlocksUntilWithinLimit(compactRoot, maxChars);
                includedBlocks = compactRoot.path("block_count").asInt();
                truncated = true;
            }
            List<String> warnings = truncated
                    ? List.of("qwen_input_truncated_by_block total=" + totalBlocks
                    + ", included=" + includedBlocks)
                    : List.of();
            return new BuildResult(
                    json,
                    ocrBlocksJson.length(),
                    json.length(),
                    totalBlocks,
                    includedBlocks,
                    truncated,
                    warnings
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not build compact Qwen OCR input", ex);
        }
    }

    private ObjectNode compactPage(JsonNode page) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyNumber(page, compact, "page_no");
        copyNumber(page, compact, "width");
        copyNumber(page, compact, "height");
        JsonNode blocks = page.path("blocks");
        if (blocks.isArray()) {
            for (JsonNode block : blocks) {
                JsonNode bodyLeftX = block.path("layout_features").path("body_left_x");
                if (bodyLeftX.isNumber()) {
                    compact.set("body_left_x", bodyLeftX);
                    break;
                }
            }
        }
        return compact;
    }

    private ObjectNode compactBlock(JsonNode block, int pageNo) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyText(block, compact, "block_id");
        compact.put("page_no", pageNo);
        copyNumber(block, compact, "order");
        String sourceType = firstText(block, "block_type", "block_label", "source_type");
        if (StringUtils.hasText(sourceType)) compact.put("source_type", sourceType);
        copyText(block, compact, "text");
        JsonNode bbox = block.get("bbox");
        if (bbox != null && bbox.isArray()) compact.set("bbox", bbox);

        JsonNode features = block.path("layout_features");
        if (features.isObject()) {
            ObjectNode compactFeatures = compact.putObject("layout_features");
            for (String name : List.of(
                    "relative_left_ratio",
                    "relative_center_ratio",
                    "relative_width_ratio",
                    "body_left_delta",
                    "is_center_like",
                    "is_header_zone",
                    "is_footer_zone",
                    "is_possible_page_number",
                    "indent_hint",
                    "gap_before",
                    "gap_after"
            )) {
                JsonNode value = features.get(name);
                if (value != null && !value.isNull()) compactFeatures.set(name, value);
            }
            if (compactFeatures.isEmpty()) compact.remove("layout_features");
        }
        return compact;
    }

    private String removeBlocksUntilWithinLimit(ObjectNode root, int maxChars) throws Exception {
        ArrayNode pages = (ArrayNode) root.path("pages");
        int included = root.path("block_count").asInt();
        while (objectMapper.writeValueAsString(root).length() > maxChars && !pages.isEmpty()) {
            ObjectNode lastPage = (ObjectNode) pages.get(pages.size() - 1);
            ArrayNode blocks = (ArrayNode) lastPage.path("blocks");
            if (!blocks.isEmpty()) {
                blocks.remove(blocks.size() - 1);
                included--;
                root.put("block_count", included);
            }
            if (blocks.isEmpty()) pages.remove(pages.size() - 1);
        }
        return objectMapper.writeValueAsString(root);
    }

    private int countBlocks(JsonNode pages) {
        int count = 0;
        for (JsonNode page : pages) {
            JsonNode blocks = page.path("blocks");
            if (blocks.isArray()) count += blocks.size();
        }
        return count;
    }

    private void copyText(JsonNode source, ObjectNode target, String name) {
        JsonNode value = source.get(name);
        if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
            target.put(name, value.asText());
        }
    }

    private void copyNumber(JsonNode source, ObjectNode target, String name) {
        JsonNode value = source.get(name);
        if (value != null && value.isNumber()) target.set(name, value);
    }

    private String firstText(JsonNode source, String... names) {
        for (String name : names) {
            JsonNode value = source.get(name);
            if (value != null && value.isTextual()) return value.asText();
        }
        return null;
    }

    public record BuildResult(
            String json,
            int originalLength,
            int compactLength,
            int totalBlocks,
            int includedBlocks,
            boolean truncated,
            List<String> warnings
    ) {
    }
}
