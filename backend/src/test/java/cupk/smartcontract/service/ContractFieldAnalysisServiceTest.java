package cupk.smartcontract.service;

import cupk.smartcontract.dto.ContractFieldAnalysisRequest;
import cupk.smartcontract.dto.ContractFieldAnalysisVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContractFieldAnalysisServiceTest {

    @Test
    void keepsRuleFieldsWhenAiReturnsNoFields() {
        QwenContractService qwen = emptyQwen();
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, null,
                "采购合同\n甲方：____\n乙方：【】\n合同金额：____元\n生效日期：____",
                "PURCHASE"));

        assertThat(result.analysisMode()).isEqualTo("QWEN_AI_ENHANCED");
        assertThat(result.notice()).contains("已基于当前合同正文识别待填写字段");
        assertThat(result.fields()).anySatisfy(field -> {
            assertThat(field.fieldName()).isEqualTo("甲方");
            assertThat(field.placeholderText()).isEqualTo("甲方：____");
            assertThat(field.status()).isEqualTo("unfilled");
            assertThat(field.blockIndex()).isEqualTo(1);
            assertThat(field.placeholderIndex()).isEqualTo(1);
        });
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("甲方", "乙方", "合同金额", "生效日期");
    }

    @Test
    void distinguishesRepeatedPartyDetailFieldsByRoleAndBlock() {
        QwenContractService qwen = emptyQwen();
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, """
                <p>甲方（委托方）：____________________________</p>
                <p>住所地：____________________________</p>
                <p>联系人：____________________</p>
                <p>乙方（服务方）：____________________________</p>
                <p>住所地：____________________________</p>
                <p>联系人：____________________</p>
                """, null, "PURCHASE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("甲方", "甲方住所地", "甲方联系人", "乙方", "乙方住所地", "乙方联系人");
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::blockIndex)
                .containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void groupsDatePlaceholdersInOneServiceTermLine() {
        QwenContractService qwen = emptyQwen();
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, "<p>本合同服务期限自______年______月______日起至______年______月______日止。</p>",
                null, "ENTERPRISE_SERVICE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("服务期限起始日期", "服务期限终止日期");
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::placeholderIndex)
                .containsExactly(1, 4);
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldType)
                .containsExactly("date", "date");
    }

    @Test
    void groupsSingleDateYearMonthDayPlaceholders() {
        QwenContractService qwen = emptyQwen();
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, "<p>生效日期：______年______月______日</p>",
                null, "PURCHASE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("生效日期");
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::placeholderText)
                .containsExactly("生效日期：______年______月______日");
    }

    @Test
    void keepsRepeatedUnderlineFieldsDistinctWithLocators() {
        QwenContractService qwen = emptyQwen();
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, "<p>甲方：____________________</p><p>乙方：____________________</p>",
                null, "OTHER"));

        assertThat(result.fields()).hasSize(2);
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::placeholderText)
                .containsExactly("甲方：____________________", "乙方：____________________");
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::blockIndex)
                .containsExactly(0, 1);
    }

    @Test
    void acceptsAiDiscoveredContractSpecificFields() throws Exception {
        QwenContractService qwen = mock(QwenContractService.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(qwen.extractRequiredFields(any(), any())).thenReturn(mapper.readTree("""
                {"fields":[{
                  "fieldKey":"deviceModel",
                  "fieldName":"设备型号",
                  "fieldType":"text",
                  "requiredLevel":"optional",
                  "placeholderText":"设备型号：____",
                  "suggestedValue":"X200",
                  "sourceText":"设备型号：____",
                  "confidence":0.91,
                  "status":"unfilled"
                }]}
                """));
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, null, "设备型号：____", "PURCHASE"));

        assertThat(result.fields()).anySatisfy(field -> {
            assertThat(field.fieldKey()).isEqualTo("deviceModel");
            assertThat(field.fieldName()).isEqualTo("设备型号");
            assertThat(field.suggestedValue()).isEqualTo("X200");
        });
    }

    @Test
    void keepsAiFieldsWithRepeatedKeysByRenamingKeys() throws Exception {
        QwenContractService qwen = mock(QwenContractService.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(qwen.extractRequiredFields(any(), any())).thenReturn(mapper.readTree("""
                {"fields":[
                {"fieldKey":"partyName","fieldName":"甲方名称","fieldType":"text","requiredLevel":"required","placeholderText":"甲方：____","suggestedValue":"","sourceText":"甲方：____","confidence":0.95,"status":"unfilled"},
                {"fieldKey":"partyName","fieldName":"乙方名称","fieldType":"text","requiredLevel":"required","placeholderText":"乙方：____","suggestedValue":"","sourceText":"乙方：____","confidence":0.95,"status":"unfilled"},
                {"fieldKey":"partyName","fieldName":"丙方名称","fieldType":"text","requiredLevel":"required","placeholderText":"丙方：____","suggestedValue":"","sourceText":"丙方：____","confidence":0.95,"status":"unfilled"}
                ]}
                """));
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, null, "甲方：____\n乙方：____\n丙方：____", "PURCHASE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("甲方名称", "乙方名称", "丙方名称");
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldKey)
                .containsExactly("partyName", "partyName_2", "partyName_3");
    }

    @Test
    void keepsAiFieldsWithSamePlaceholderText() throws Exception {
        QwenContractService qwen = mock(QwenContractService.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(qwen.extractRequiredFields(any(), any())).thenReturn(mapper.readTree("""
                {"fields":[
                {"fieldKey":"partyAName","fieldName":"甲方名称","fieldType":"text","requiredLevel":"required","placeholderText":"____","suggestedValue":"","sourceText":"甲方 ____","confidence":0.95,"status":"unfilled"},
                {"fieldKey":"partyBName","fieldName":"乙方名称","fieldType":"text","requiredLevel":"required","placeholderText":"____","suggestedValue":"","sourceText":"乙方 ____","confidence":0.95,"status":"unfilled"}
                ]}
                """));
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, null, "甲方 ____\n乙方 ____", "PURCHASE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("甲方名称", "乙方名称");
        assertThat(result.fields()).hasSize(2);
    }

    @Test
    void aiReplacementConsumesOnlyOneMatchingRuleField() throws Exception {
        QwenContractService qwen = mock(QwenContractService.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(qwen.extractRequiredFields(any(), any())).thenReturn(mapper.readTree("""
                {"fields":[
                {"fieldKey":"partyAName","fieldName":"甲方名称","fieldType":"text","requiredLevel":"required","placeholderText":"____","suggestedValue":"","sourceText":"甲方 ____","confidence":0.95,"status":"unfilled"}
                ]}
                """));
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, null, "甲方：____\n乙方：____", "PURCHASE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("甲方名称", "乙方");
        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::placeholderText)
                .containsExactly("____", "乙方：____");
    }

    @Test
    void ruleFallbackNamesSmallAndUppercaseAmountFields() {
        QwenContractService qwen = emptyQwen();
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, null, "合同金额：____元（大写）：____", "PURCHASE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("合同金额小写", "合同金额大写");
    }

    @Test
    void ignoresSignatureAreaFieldsInRuleFallback() {
        QwenContractService qwen = emptyQwen();
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, """
                <p>甲方：____</p>
                <div class="signature-block" data-doc-style="SIGNATURE_BLOCK">
                  <div class="signature-row"><span>签名：____（盖章）</span><span>签名：____（盖章）</span></div>
                  <div class="signature-row"><span>签署日期：____年____月____日</span></div>
                </div>
                """, null, "PURCHASE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("甲方");
    }

    @Test
    void propagatesQwenFailureBecauseAnalyzeHasNoFallback() {
        QwenContractService qwen = unavailableQwen();
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        assertThatThrownBy(() -> service.analyze(new ContractFieldAnalysisRequest(
                null, null, null, "甲方：____", "PURCHASE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Qwen unavailable");
    }

    @Test
    void ignoresSignatureAreaFieldsReturnedByAi() throws Exception {
        QwenContractService qwen = mock(QwenContractService.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(qwen.extractRequiredFields(any(), any())).thenReturn(mapper.readTree("""
                {"fields":[
                {"fieldKey":"partyAName","fieldName":"甲方名称","fieldType":"text","requiredLevel":"required","placeholderText":"甲方：____","suggestedValue":"","sourceText":"甲方：____","confidence":0.95,"status":"unfilled"},
                {"fieldKey":"signDate","fieldName":"签署日期","fieldType":"date","requiredLevel":"optional","placeholderText":"签署日期：____年____月____日","suggestedValue":"","sourceText":"签署日期：____年____月____日","confidence":0.9,"status":"unfilled"},
                {"fieldKey":"partyASign","fieldName":"甲方签名","fieldType":"text","requiredLevel":"optional","placeholderText":"签名：____","suggestedValue":"","sourceText":"签名：____（盖章）","confidence":0.9,"status":"unfilled"}
                ]}
                """));
        ContractFieldAnalysisService service = new ContractFieldAnalysisService(qwen);

        ContractFieldAnalysisVO result = service.analyze(new ContractFieldAnalysisRequest(
                null, null, null, "甲方：____\n签名：____（盖章）\n签署日期：____年____月____日", "PURCHASE"));

        assertThat(result.fields()).extracting(ContractFieldAnalysisVO.ContractField::fieldName)
                .containsExactly("甲方名称");
    }

    private QwenContractService unavailableQwen() {
        QwenContractService qwen = mock(QwenContractService.class);
        when(qwen.extractRequiredFields(any(), any()))
                .thenThrow(new IllegalStateException("Qwen unavailable"));
        return qwen;
    }

    private QwenContractService emptyQwen() {
        QwenContractService qwen = mock(QwenContractService.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(qwen.extractRequiredFields(any(), any()))
                .thenReturn(mapper.createObjectNode().set("fields", mapper.createArrayNode()));
        return qwen;
    }
}
