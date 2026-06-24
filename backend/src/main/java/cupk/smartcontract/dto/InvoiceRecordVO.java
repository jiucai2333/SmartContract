package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoiceRecordVO(
        Long invoiceId,
        Long paymentPlanId,
        Long contractId,
        String contractTitle,
        String phaseName,
        String invoiceNo,
        BigDecimal invoiceAmount,
        LocalDate invoiceDate,
        String invoiceStatus,
        Long fileId,
        String fileName,
        String downloadUrl,
        String remark,
        LocalDateTime createdAt
) {
}
