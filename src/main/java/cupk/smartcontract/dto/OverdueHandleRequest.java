package cupk.smartcontract.dto;

import java.time.LocalDate;

public record OverdueHandleRequest(
        String action,
        LocalDate actualCompletedDate,
        LocalDate newPlannedDate,
        String delayReason,
        String disposalRemark
) {
}
