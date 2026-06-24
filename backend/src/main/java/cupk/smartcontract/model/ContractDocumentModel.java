package cupk.smartcontract.model;

import java.util.List;

public record ContractDocumentModel(
        String plainText,
        String editorHtml,
        String previewHtml,
        List<Block> blocks,
        Integer pageCount,
        String ocrRawJson,
        String ocrBlocksJson,
        List<String> warnings,
        String source,
        String ocrModel,
        Long ocrDurationMs
) {
    public ContractDocumentModel {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Block(
            String text,
            String label,
            List<Double> bbox,
            int pageIndex,
            int order
    ) {
        public Block {
            bbox = bbox == null ? List.of() : List.copyOf(bbox);
        }
    }
}
