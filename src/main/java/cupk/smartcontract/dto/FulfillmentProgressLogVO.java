package cupk.smartcontract.dto;

import java.time.LocalDateTime;

public record FulfillmentProgressLogVO(
        Long logId,
        Long planId,
        Long contractId,
        String operation,
        String beforeStatus,
        String afterStatus,
        String beforeValue,
        String afterValue,
        Long operatorId,
        String operatorName,
        LocalDateTime operateTime,
        String remark,
        String clientIp
) {
}
