package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.dto.OcrPipelineVO;
import cupk.smartcontract.model.ContractDocumentModel;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ContractDocumentImportService {
    private final OcrService ocrService;
    private final DocumentParseService documentParseService;
    private final QwenContractService qwenContractService;
    private final OcrEditorHtmlService htmlService;
    private final ObjectMapper objectMapper;

    public ContractDocumentImportService(OcrService ocrService,
                                         DocumentParseService documentParseService,
                                         QwenContractService qwenContractService,
                                         OcrEditorHtmlService htmlService,
                                         ObjectMapper objectMapper) {
        this.ocrService = ocrService;
        this.documentParseService = documentParseService;
        this.qwenContractService = qwenContractService;
        this.htmlService = htmlService;
        this.objectMapper = objectMapper;
    }

    public ContractDocumentModel importDocument(Path path, String fileType, boolean preserveFormat)
            throws Exception {
        ContractDocumentModel source = process(path, fileType, preserveFormat);
        if (StringUtils.hasText(source.ocrBlocksJson())) {
            String editorHtml = htmlService.buildEditableHtml(source.ocrBlocksJson());
            String previewHtml = htmlService.build(source.ocrBlocksJson()).html();
            if (!StringUtils.hasText(editorHtml)) {
                editorHtml = htmlService.buildPlainTextHtml(source.plainText());
            }
            if (!StringUtils.hasText(previewHtml)) previewHtml = editorHtml;
            return new ContractDocumentModel(
                    source.plainText(), editorHtml, previewHtml, source.blocks(),
                    source.pageCount(), source.ocrRawJson(), source.ocrBlocksJson(),
                    source.warnings(), source.source(), source.ocrModel(), source.ocrDurationMs());
        }
        return source;
    }

    private ContractDocumentModel process(Path path, String fileType, boolean preserveFormat)
            throws Exception {
        String normalized = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        if (isPaddleType(normalized)) {
            OcrService.OcrProcessResult result = ocrService.process(path, normalized);
            if ((result.rawText() == null || result.rawText().isBlank())
                    && (result.parseJson() == null || result.parseJson().isBlank())) {
                throw new IllegalStateException("OCR did not return usable contract content");
            }
            return new ContractDocumentModel(
                    result.rawText(), null, null, readBlocks(result.parseJson()),
                    result.pageCount(), result.rawJson(), result.parseJson(),
                    result.warnings(), result.source(), result.model(), result.durationMs());
        }

        DocumentParseService.ParseResult result =
                documentParseService.parse(path, normalized, preserveFormat);
        return new ContractDocumentModel(
                Jsoup.parse(result.html()).text(), result.html(), result.html(), List.of(),
                result.pageCount(), null, null, List.of(), result.parser(), null, null);
    }

    private boolean isPaddleType(String fileType) {
        return List.of("jpg", "jpeg", "png", "webp").contains(fileType);
    }

    private List<ContractDocumentModel.Block> readBlocks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            OcrPipelineVO document = objectMapper.readValue(json, OcrPipelineVO.class);
            List<ContractDocumentModel.Block> blocks = new ArrayList<>();
            if (document.pages() == null) return blocks;
            for (OcrPipelineVO.Page page : document.pages()) {
                if (page == null || page.blocks() == null) continue;
                for (OcrPipelineVO.Block block : page.blocks()) {
                    if (block == null) continue;
                    blocks.add(new ContractDocumentModel.Block(
                            block.text(), block.blockType(), block.bbox(),
                            page.pageNo(), block.order()));
                }
            }
            return blocks;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read normalized OCR blocks", ex);
        }
    }

    public String normalizeEditorHtml(String html) {
        return Jsoup.clean(html == null ? "" : html, Safelist.relaxed()
                .addAttributes("p", "style", "data-text-indent", "data-indent", "class",
                        "data-doc-style", "data-align", "data-indent-chars", "data-column")
                .addAttributes("div", "class", "style", "data-page-no", "data-block-id",
                        "data-block-type", "data-align", "data-font-size-level",
                        "data-bbox-source", "data-confidence", "data-text-indent", "data-indent")
                .addAttributes("h1", "style", "data-doc-style", "data-align",
                        "data-indent-chars")
                .addAttributes("h2", "style", "data-doc-style", "data-align",
                        "data-indent-chars")
                .addAttributes("h3", "style", "data-doc-style", "data-align",
                        "data-indent-chars")
                .addAttributes("span", "style")
                .addAttributes("table", "class", "style")
                .addAttributes("td", "colspan", "rowspan", "style")
                .addAttributes("th", "colspan", "rowspan", "style"));
    }

    public JsonNode extractFields(ContractDocumentModel document) {
        return qwenContractService.extractFields(document.plainText());
    }

    public JsonNode structureClauses(ContractDocumentModel document) {
        return qwenContractService.structureClauses(document.plainText());
    }

    public JsonNode suggestChanges(ContractDocumentModel document) {
        return qwenContractService.suggestChanges(document.plainText());
    }
}
