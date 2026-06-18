package cupk.smartcontract.service;

import cupk.smartcontract.dto.OcrDocumentVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class LayoutFeatureService {
    private static final Pattern PURE_NUMBER = Pattern.compile("^\\d+$");
    private static final Pattern LIST_ITEM = Pattern.compile(
            "^\\s*(?:\\d+[、.]|[（(]\\d+[）)])\\s*.*");
    /** 第X条/第X章/第X节 等法律条文编号，不应被视作首行缩进 */
    private static final Pattern ARTICLE_HEADING = Pattern.compile(
            "^\\s*第[一二三四五六七八九十百千\\d]+[条章节款段].*");
    /** 以中文数字开头的短文本，高度可能是标题 */
    private static final Pattern CHINESE_NUMERAL_START = Pattern.compile(
            "^\\s*[一二三四五六七八九十]+[、．.].*");

    public OcrDocumentVO generate(OcrDocumentVO document, List<String> warnings) {
        if (document == null || document.pages() == null) return document;
        List<OcrDocumentVO.Page> pages = document.pages().stream()
                .map(page -> generatePage(page, warnings))
                .toList();
        return new OcrDocumentVO(
                document.fileType(),
                document.parseSource(),
                document.approximate(),
                pages,
                document.markdown(),
                document.warnings()
        );
    }

    private OcrDocumentVO.Page generatePage(OcrDocumentVO.Page page, List<String> warnings) {
        if (page == null || page.blocks() == null) return page;
        if (page.width() == null || page.height() == null
                || page.width() <= 0 || page.height() <= 0) {
            warnings.add("Page " + page.pageNo()
                    + " layout_features_skipped reason=missing_page_size_or_bbox");
            return page;
        }

        double pageWidth = page.width();
        double pageHeight = page.height();
        Double bodyLeftX = calculateBodyLeftX(page.blocks(), pageWidth, pageHeight);
        List<OcrDocumentVO.Block> ordered = page.blocks().stream()
                .sorted(Comparator.comparingInt(OcrDocumentVO.Block::order))
                .toList();
        Map<String, Gaps> gaps = calculateGaps(ordered);
        List<OcrDocumentVO.Block> blocks = new ArrayList<>(page.blocks().size());
        int generated = 0;
        int pageNumbers = 0;

        for (OcrDocumentVO.Block block : page.blocks()) {
            Bbox bbox = validBbox(block);
            if (bbox == null) {
                blocks.add(block);
                continue;
            }
            OcrDocumentVO.LayoutFeatures features = buildFeatures(
                    block, bbox, pageWidth, pageHeight, bodyLeftX,
                    gaps.getOrDefault(block.blockId(), Gaps.EMPTY));
            blocks.add(copyWithFeatures(block, features));
            generated++;
            if (features.possiblePageNumber()) pageNumbers++;
        }

        if (generated == 0) {
            warnings.add("Page " + page.pageNo()
                    + " layout_features_skipped reason=missing_page_size_or_bbox");
        } else {
            warnings.add("Page " + page.pageNo() + " layout_features_generated count=" + generated);
            warnings.add("Page " + page.pageNo() + " body_left_x="
                    + (bodyLeftX == null ? "null" : format(bodyLeftX)));
            warnings.add("Page " + page.pageNo()
                    + " possible_page_number count=" + pageNumbers);
        }
        return new OcrDocumentVO.Page(page.pageNo(), page.width(), page.height(), blocks);
    }

    private Double calculateBodyLeftX(List<OcrDocumentVO.Block> blocks,
                                      double pageWidth, double pageHeight) {
        double bucketSize = Math.max(1.0, pageWidth * 0.02);
        Map<Long, List<Double>> buckets = new LinkedHashMap<>();
        for (OcrDocumentVO.Block block : blocks) {
            Bbox bbox = validBbox(block);
            if (bbox == null || !isBodyCandidate(block, bbox, pageWidth, pageHeight)) continue;
            long bucket = Math.round(bbox.x0() / bucketSize);
            buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(bbox.x0());
        }
        return buckets.values().stream()
                .max(Comparator.<List<Double>>comparingInt(List::size)
                        .thenComparingDouble(values -> -median(values)))
                .map(this::median)
                .orElse(null);
    }

    private boolean isBodyCandidate(OcrDocumentVO.Block block, Bbox bbox,
                                    double pageWidth, double pageHeight) {
        String type = lower(block.blockType());
        if (!(type.contains("text") || type.contains("paragraph")
                || type.contains("heading") || type.contains("title")
                || type.equals("unknown"))) {
            return false;
        }
        if (type.equals("page_number") || type.equals("number")) return false;
        if (bbox.y0() > pageHeight * 0.90) return false;
        double blockWidth = bbox.x1() - bbox.x0();
        double centerX = (bbox.x0() + bbox.x1()) / 2.0;
        boolean centeredTitle = blockWidth < pageWidth * 0.45
                && Math.abs(centerX - pageWidth / 2.0) <= pageWidth * 0.08;
        return !centeredTitle;
    }

    private Map<String, Gaps> calculateGaps(List<OcrDocumentVO.Block> ordered) {
        Map<String, Gaps> result = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            OcrDocumentVO.Block current = ordered.get(i);
            Bbox currentBbox = validBbox(current);
            if (currentBbox == null) continue;
            Double before = adjacentGap(ordered, i, -1, currentBbox.y0(), true);
            Double after = adjacentGap(ordered, i, 1, currentBbox.y1(), false);
            result.put(current.blockId(), new Gaps(before, after));
        }
        return result;
    }

    private Double adjacentGap(List<OcrDocumentVO.Block> ordered, int index, int direction,
                               double edge, boolean before) {
        int adjacentIndex = index + direction;
        if (adjacentIndex < 0 || adjacentIndex >= ordered.size()) return null;
        Bbox adjacent = validBbox(ordered.get(adjacentIndex));
        if (adjacent == null) return null;
        return before ? edge - adjacent.y1() : adjacent.y0() - edge;
    }

    private OcrDocumentVO.LayoutFeatures buildFeatures(
            OcrDocumentVO.Block block, Bbox bbox, double pageWidth, double pageHeight,
            Double bodyLeftX, Gaps gaps) {
        double blockWidth = bbox.x1() - bbox.x0();
        double blockHeight = bbox.y1() - bbox.y0();
        double centerX = (bbox.x0() + bbox.x1()) / 2.0;
        double centerY = (bbox.y0() + bbox.y1()) / 2.0;
        Double bodyLeftDelta = bodyLeftX == null ? null : bbox.x0() - bodyLeftX;
        boolean centerLike = Math.abs(centerX - pageWidth / 2.0) <= pageWidth * 0.08
                && blockWidth <= pageWidth * 0.65;
        boolean footerZone = bbox.y1() >= pageHeight * 0.92;
        String text = block.text() == null ? "" : block.text().trim();
        boolean possiblePageNumber = (PURE_NUMBER.matcher(text).matches()
                && footerZone && centerLike)
                || "number".equals(lower(block.blockType()))
                || "page_number".equals(lower(block.blockType()));
        String indentHint = indentHint(
                text, bbox.x0(), bbox.y0(), blockWidth, pageWidth, pageHeight,
                bodyLeftDelta, centerLike, possiblePageNumber);

        return new OcrDocumentVO.LayoutFeatures(
                bbox.x0(), bbox.y0(), bbox.x1(), bbox.y1(),
                blockWidth, blockHeight, centerX, centerY,
                bbox.x0() / pageWidth, bbox.y0() / pageHeight,
                bbox.x1() / pageWidth, bbox.y1() / pageHeight,
                blockWidth / pageWidth, blockHeight / pageHeight,
                centerX / pageWidth,
                bodyLeftX, bodyLeftDelta,
                bodyLeftDelta != null && Math.abs(bodyLeftDelta) <= pageWidth * 0.02,
                centerLike,
                bbox.x0() > pageWidth * 0.55,
                bbox.y0() <= pageHeight * 0.08,
                footerZone,
                possiblePageNumber,
                indentHint,
                gaps.before(),
                gaps.after()
        );
    }

    private String indentHint(String text, double x0, double y0, double blockWidth,
                              double pageWidth, double pageHeight, Double bodyLeftDelta,
                              boolean centerLike, boolean possiblePageNumber) {
        if (possiblePageNumber) return "footer_number";
        boolean signatureText = text.contains("甲方") || text.contains("乙方")
                || text.contains("签名") || text.contains("签署日期");
        if (signatureText && x0 < pageWidth * 0.45) return "signature_left";
        if (signatureText && x0 > pageWidth * 0.45) return "signature_right";
        if (centerLike && y0 <= pageHeight * 0.30
                && blockWidth <= pageWidth * 0.45 && text.length() <= 40) {
            return "center_title";
        }
        // 法律条文编号如“第X条”不应被视作首行缩进
        if (ARTICLE_HEADING.matcher(text).matches()) return "article_heading";
        // 中文数字编号如“一、”通常为标题
        if (CHINESE_NUMERAL_START.matcher(text).matches() && text.length() <= 30) return "chinese_heading";
        if (LIST_ITEM.matcher(text).matches()) return "list_item";
        // 降低阈值：使用 pageWidth * 0.01 且至少 10px（兼容低 DPI），
        // 同时确保缩进不超过页面宽度的 20%（排除严重偏移的误识别块）
        if (bodyLeftDelta != null && bodyLeftDelta > pageWidth * 0.01
                && bodyLeftDelta >= 10.0 && bodyLeftDelta < pageWidth * 0.20) {
            return "first_line";
        }
        return "none";
    }

    private OcrDocumentVO.Block copyWithFeatures(
            OcrDocumentVO.Block block, OcrDocumentVO.LayoutFeatures features) {
        return new OcrDocumentVO.Block(
                block.blockId(), block.blockType(), block.text(), block.bbox(),
                block.bboxSource(), block.align(), block.fontSizeLevel(),
                block.confidence(), block.order(), block.source(),
                block.approximate(), block.table(), features
        );
    }

    private Bbox validBbox(OcrDocumentVO.Block block) {
        if (block == null || block.bbox() == null || block.bbox().size() != 4) return null;
        double x0 = block.bbox().get(0);
        double y0 = block.bbox().get(1);
        double x1 = block.bbox().get(2);
        double y1 = block.bbox().get(3);
        if (!Double.isFinite(x0) || !Double.isFinite(y0)
                || !Double.isFinite(x1) || !Double.isFinite(y1)
                || x1 < x0 || y1 < y0) {
            return null;
        }
        return new Bbox(x0, y0, x1, y1);
    }

    private double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0
                : sorted.get(middle);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record Bbox(double x0, double y0, double x1, double y1) {
    }

    private record Gaps(Double before, Double after) {
        private static final Gaps EMPTY = new Gaps(null, null);
    }
}
