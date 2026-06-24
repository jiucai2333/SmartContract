package cupk.smartcontract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record QwenLayoutVO(
        List<Block> blocks,
        List<String> warnings
) {
    public record Block(
            @JsonProperty("block_id") String blockId,
            @JsonProperty("block_type") String blockType,
            @JsonProperty("normalized_text") String normalizedText,
            int order,
            String align,
            @JsonProperty("font_size_level") String fontSizeLevel,
            String reason
    ) {
    }
}
