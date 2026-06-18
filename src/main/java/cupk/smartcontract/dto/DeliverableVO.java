package cupk.smartcontract.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeliverableVO {
    private Long deliverableId;
    private Long planId;
    private Long contractId;
    private String deliverableType;
    private String deliverableTypeName;
    private String itemName;
    private String contractStage;
    private Integer isConfirmed;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
