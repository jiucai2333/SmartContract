package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRecordVO(
        Long paymentRecordId,
        Long paymentPlanId,
        Long contractId,
        String contractTitle,
        String phaseName,
        BigDecimal paidAmount,
        LocalDate paidDate,
        String bankSerialNo,
        String handlerName,
        Long voucherFileId,
        String voucherFileName,
        String voucherDownloadUrl,
        String payer,
        String receiver,
        String remark
) {
}
