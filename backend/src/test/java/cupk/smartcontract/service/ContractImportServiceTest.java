package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.dto.ContractImportResultVO;
import cupk.smartcontract.dto.OcrPipelineVO;
import cupk.smartcontract.entity.ContractAttachmentOcr;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ContractImportServiceTest {

    @Test
    void exposesEditorHtmlWithoutLegacyQwenLayoutMetadata() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OcrEditorHtmlService htmlService = new OcrEditorHtmlService(objectMapper);
        ContractImportService service = new ContractImportService(
                mock(ContractAttachmentService.class),
                mock(cupk.smartcontract.mapper.ContractAttachmentOcrMapper.class),
                mock(cupk.smartcontract.mapper.ContractMainMapper.class),
                mock(FileStorageService.class),
                mock(ContractDocumentImportService.class),
                htmlService,
                mock(ContractManagementService.class),
                mock(ContractVersionService.class),
                objectMapper);
        OcrPipelineVO.Block block = new OcrPipelineVO.Block(
                "p1_b1", "paragraph", "Contract body",
                List.of(100.0, 100.0, 900.0, 150.0),
                "block_bbox", "left", "normal", 0.99, 1,
                "paddleocr", true, null, null);
        OcrPipelineVO document = new OcrPipelineVO(
                "pdf", "paddleocr", true,
                List.of(new OcrPipelineVO.Page(1, 1000, 1400, List.of(block))),
                null, List.of());
        ContractAttachmentOcr ocr = new ContractAttachmentOcr();
        ocr.setAttachmentId(12L);
        ocr.setOcrStatus("SUCCESS");
        ocr.setOcrBlocksJson(objectMapper.writeValueAsString(document));
        ocr.setQwenLayoutJson("{\"blocks\":[]}");
        ocr.setQwenModel("legacy-layout-model");
        ocr.setQwenDurationMs(25L);

        ContractImportResultVO result = service.buildImportResultVo(ocr, "contract.pdf");

        assertThat(result.editorHtml()).contains("Contract body");
        assertThat(result.qwenLayoutJsonExist()).isFalse();
        assertThat(result.qwenModel()).isNull();
        assertThat(result.qwenDurationMs()).isNull();
    }

    @Test
    void prefersPersistedEditorHtmlWhenPresent() {
        ObjectMapper objectMapper = new ObjectMapper();
        OcrEditorHtmlService htmlService = new OcrEditorHtmlService(objectMapper);
        ContractImportService service = new ContractImportService(
                mock(ContractAttachmentService.class),
                mock(cupk.smartcontract.mapper.ContractAttachmentOcrMapper.class),
                mock(cupk.smartcontract.mapper.ContractMainMapper.class),
                mock(FileStorageService.class),
                mock(ContractDocumentImportService.class),
                htmlService,
                mock(ContractManagementService.class),
                mock(ContractVersionService.class),
                objectMapper);
        ContractAttachmentOcr ocr = new ContractAttachmentOcr();
        ocr.setAttachmentId(12L);
        ocr.setOcrStatus("SUCCESS");
        ocr.setEditorHtml("<h1 data-doc-style=\"TITLE\">Persisted</h1>");
        ocr.setPlainText("Dynamic fallback");

        ContractImportResultVO result = service.buildImportResultVo(ocr, "contract.pdf");

        assertThat(result.editorHtml()).isEqualTo("<h1 data-doc-style=\"TITLE\">Persisted</h1>");
    }
}
