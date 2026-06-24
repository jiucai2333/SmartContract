package cupk.smartcontract.dto;

import java.math.BigDecimal;

public record OcrExtractVO(
        String contractType,
        String partyA,
        String partyB,
        String title,
        String counterparty,
        BigDecimal amount,
        String businessScope,
        String specialTerms,
        String rawText
) {
}
