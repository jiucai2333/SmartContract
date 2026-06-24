package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRecordRequest(
        BigDecimal paidAmount,
        LocalDate paidDate,
        String bankSerialNo,
        String handlerName,
        Long voucherFileId,
        String payer,
        String receiver,
        String remark
) {
}
