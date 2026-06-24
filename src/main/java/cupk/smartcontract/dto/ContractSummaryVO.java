package cupk.smartcontract.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ContractSummaryVO {
    private Long contractId;
    private String contractNo;
    private String title;
    private BigDecimal amount;
}
