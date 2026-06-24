package cupk.smartcontract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OcrPipelineVO(
        @JsonProperty("file_type") String fileType,
        @JsonProperty("parse_source") String parseSource,
        boolean approximate,
        List<Page> pages,
        String markdown,
        List<String> warnings
) {
    public record Page(
            @JsonProperty("page_no") int pageNo,
            Integer width,
            Integer height,
            List<Block> blocks
    ) {
    }

    public record Block(
            @JsonProperty("block_id") String blockId,
            @JsonProperty("block_type") String blockType,
            String text,
            List<Double> bbox,
            @JsonProperty("bbox_source") String bboxSource,
            String align,
            @JsonProperty("font_size_level") String fontSizeLevel,
            Double confidence,
            int order,
            String source,
            boolean approximate,
            Object table,
            @JsonProperty("layout_features") LayoutFeatures layoutFeatures
    ) {
    }

    public record LayoutFeatures(
            double x0,
            double y0,
            double x1,
            double y1,
            @JsonProperty("block_width") double blockWidth,
            @JsonProperty("block_height") double blockHeight,
            @JsonProperty("center_x") double centerX,
            @JsonProperty("center_y") double centerY,
            @JsonProperty("relative_left_ratio") double relativeLeftRatio,
            @JsonProperty("relative_top_ratio") double relativeTopRatio,
            @JsonProperty("relative_right_ratio") double relativeRightRatio,
            @JsonProperty("relative_bottom_ratio") double relativeBottomRatio,
            @JsonProperty("relative_width_ratio") double relativeWidthRatio,
            @JsonProperty("relative_height_ratio") double relativeHeightRatio,
            @JsonProperty("relative_center_ratio") double relativeCenterRatio,
            @JsonProperty("body_left_x") Double bodyLeftX,
            @JsonProperty("body_left_delta") Double bodyLeftDelta,
            @JsonProperty("is_left_aligned_to_body") boolean leftAlignedToBody,
            @JsonProperty("is_center_like") boolean centerLike,
            @JsonProperty("is_right_like") boolean rightLike,
            @JsonProperty("is_header_zone") boolean headerZone,
            @JsonProperty("is_footer_zone") boolean footerZone,
            @JsonProperty("is_possible_page_number") boolean possiblePageNumber,
            @JsonProperty("indent_hint") String indentHint,
            @JsonProperty("gap_before") Double gapBefore,
            @JsonProperty("gap_after") Double gapAfter
    ) {
    }
}
