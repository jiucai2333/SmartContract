package cupk.smartcontract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceRecordRequest(
        String invoiceNo,
        BigDecimal invoiceAmount,
        LocalDate invoiceDate,
        String invoiceStatus,
        Long fileId,
        String remark
) {
}
