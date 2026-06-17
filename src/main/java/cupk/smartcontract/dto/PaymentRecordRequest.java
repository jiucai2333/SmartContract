package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRecordRequest(
        BigDecimal paidAmount,
        LocalDate paidDate,
        String payer,
        String receiver,
        String remark
) {
}
