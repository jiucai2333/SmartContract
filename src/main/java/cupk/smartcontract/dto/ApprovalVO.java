package cupk.smartcontract.dto;

import cupk.smartcontract.entity.ApprovalRecord;

import java.time.LocalDateTime;
import java.util.List;

public record ApprovalVO(
        Long instanceId,
        Long contractId,
        String contractNo,
        String contractTitle,
        String flowType,
        String currentNode,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        List<ApprovalRecord> records
) {
}
