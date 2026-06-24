package cupk.smartcontract.dto;

import java.time.LocalDateTime;

public record DeliverableVO(
        Long deliverableId,
        Long planId,
        Long contractId,
        String contractTitle,
        String planName,
        String planType,
        String deliverableType,
        String deliverableName,
        String stageName,
        String confirmMethod,
        String deliverableStatus,
        String confirmStatus,
        String sourceClause,
        java.math.BigDecimal aiConfidence,
        Boolean aiExtracted,
        Boolean confirmed,
        String confirmer,
        LocalDateTime confirmedAt,
        Boolean acceptancePassed,
        String acceptedBy,
        LocalDateTime acceptedAt,
        String submittedBy,
        LocalDateTime submittedAt,
        String reviewerName,
        LocalDateTime reviewedAt,
        String reviewComment,
        Integer submissionVersion,
        Long fileId,
        String fileName,
        String downloadUrl,
        String remark
) {
    public static String typeName(String type) {
        return switch (type) {
            case "DESIGN_DOC" -> "需求设计文档";
            case "SOURCE_CODE" -> "源代码";
            case "RUNNABLE_PROGRAM" -> "可运行程序";
            case "ACCEPTANCE_REPORT" -> "验收报告";
            default -> type;
        };
    }
}
