package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.QwenProperties;
import cupk.smartcontract.dto.AiRiskVO;
import org.junit.jupiter.api.Test;

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
}
