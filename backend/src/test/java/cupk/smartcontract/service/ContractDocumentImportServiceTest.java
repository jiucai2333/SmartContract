package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.model.ContractDocumentModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractDocumentImportServiceTest {

    @Test
    void importsOfficeDocumentWithoutRoutingThroughOcr() throws Exception {
        OcrService ocrService = mock(OcrService.class);
        DocumentParseService documentParseService = mock(DocumentParseService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ContractDocumentImportService service = new ContractDocumentImportService(
                ocrService,
                documentParseService,
                mock(QwenContractService.class),
                new OcrEditorHtmlService(objectMapper),
                objectMapper);
        Path path = Path.of("contract.docx");
        when(documentParseService.parse(path, "docx", true))
                .thenReturn(new DocumentParseService.ParseResult(
                        "<h1>Contract</h1><p>Body</p>", 1, "DOCX_POI"));

        ContractDocumentModel result = service.importDocument(path, "DOCX", true);

        assertThat(result.plainText()).isEqualTo("Contract Body");
        assertThat(result.editorHtml()).isEqualTo("<h1>Contract</h1><p>Body</p>");
        assertThat(result.previewHtml()).isEqualTo(result.editorHtml());
        assertThat(result.source()).isEqualTo("DOCX_POI");
        assertThat(result.ocrBlocksJson()).isNull();
    }

    @Test
    void importsTextPdfWithPdfParserBeforeOcrFallback() throws Exception {
        OcrService ocrService = mock(OcrService.class);
        DocumentParseService documentParseService = mock(DocumentParseService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ContractDocumentImportService service = new ContractDocumentImportService(
                ocrService,
                documentParseService,
                mock(QwenContractService.class),
                new OcrEditorHtmlService(objectMapper),
                objectMapper);
        Path path = Path.of("contract.pdf");
        when(documentParseService.parse(path, "pdf", true))
                .thenReturn(new DocumentParseService.ParseResult(
                        "<p>第一页内容</p><p>第六页签署区</p>", 6, "PDFBOX"));

        ContractDocumentModel result = service.importDocument(path, "PDF", true);

        assertThat(result.plainText()).isEqualTo("第一页内容 第六页签署区");
        assertThat(result.editorHtml()).contains("第一页内容", "第六页签署区");
        assertThat(result.pageCount()).isEqualTo(6);
        assertThat(result.source()).isEqualTo("PDFBOX");
        verify(ocrService, never()).process(path, "pdf");
    }
}
