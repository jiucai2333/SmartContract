package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliverableRequest {
    private Long deliverableId;
    @NotNull private Long planId;
    @NotNull private Long contractId;
    @NotBlank private String deliverableType;
    @NotBlank private String itemName;
    @NotBlank private String contractStage;
    private Integer sortOrder;
}
