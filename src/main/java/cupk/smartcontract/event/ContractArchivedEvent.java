package cupk.smartcontract.event;

import java.time.LocalDateTime;

public record ContractArchivedEvent(
        Long contractId,
        Long versionId,
        LocalDateTime archiveTime
) {
}
