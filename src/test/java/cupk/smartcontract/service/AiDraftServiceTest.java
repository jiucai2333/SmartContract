package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.QwenProperties;
import cupk.smartcontract.dto.AiRiskVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiDraftServiceTest {

    @Test
    void fillsExplicitMissingContractClausesWhenModelReturnsNoRisk() {
        AiDraftService service = new AiDraftService(
                new QwenProperties("", "", "qwen3-vl-plus", "qwen3-vl-plus", false, 1024, 1, false),
                new ObjectMapper(),
                null,
                null
        );
        String contractText = "甲方委托乙方开发软件系统，合同金额100000元。双方确认项目完成后交付，"
                + "但未约定付款时间、验收标准、违约责任、知识产权归属、保密义务和争议解决方式。";

        List<AiRiskVO> risks = service.mergeRuleBasedMissingRisks(contractText, List.of());

        assertThat(risks)
                .extracting(AiRiskVO::clause)
                .contains(
                        "缺失：付款时间",
                        "缺失：验收标准",
                        "缺失：违约责任",
                        "缺失：知识产权归属",
                        "缺失：保密义务",
                        "缺失：争议解决方式"
                );
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesFulfillmentNodesReturnedByModel() throws Exception {
        AiDraftService service = new AiDraftService(
                new QwenProperties("", "", "qwen3-vl-plus", "qwen3-vl-plus", false, 1024, 1, false),
                new ObjectMapper(),
                null,
                null
        );
        String response = """
                {"nodes":[
                  {"nodeName":"首付款支付","nodeType":"PAYMENT","plannedDate":"2026-07-15","responsibleParty":"甲方","sourceClause":"第4条 付款安排","confidence":0.92},
                  {"nodeName":"系统验收","nodeType":"ACCEPTANCE","plannedDate":"2026-08-01","responsibleParty":"双方","sourceClause":"第6条 验收","confidence":0.88},
                  {"nodeName":"质保到期","nodeType":"WARRANTY","plannedDate":"2026-12-31","responsibleParty":"乙方","sourceClause":"第8条 质保期","aiConfidence":"78%"}
                ]}
                """;

        Method method = AiDraftService.class.getDeclaredMethod("parseFulfillmentNodes", String.class);
        method.setAccessible(true);
        List<AiDraftService.FulfillmentNode> nodes =
                (List<AiDraftService.FulfillmentNode>) method.invoke(service, response);

        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).nodeName()).isEqualTo("首付款支付");
        assertThat(nodes.get(0).nodeType()).isEqualTo("PAYMENT");
        assertThat(nodes.get(0).plannedDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(nodes.get(0).confidence()).isEqualTo(0.92);
        assertThat(nodes.get(0).aiExtracted()).isTrue();
        assertThat(nodes.get(0).dateConfirmed()).isTrue();
        assertThat(nodes.get(2).confidence()).isEqualTo(0.78);
    }
}
