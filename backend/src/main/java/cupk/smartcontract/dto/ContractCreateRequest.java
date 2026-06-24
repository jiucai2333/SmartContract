package cupk.smartcontract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractCreateRequest(
        @NotBlank String title,
        @NotBlank String type,
        @NotNull BigDecimal amount,
        @NotBlank String counterparty,
        @NotNull Long deptId,
        @NotNull Long ownerId,
        Long templateId,
        LocalDate signDate,
        LocalDate dueDate
) {
}
