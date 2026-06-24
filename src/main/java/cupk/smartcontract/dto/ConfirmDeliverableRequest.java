package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmDeliverableRequest {
    @NotNull private Long deliverableId;
    private String confirmedBy;
}
