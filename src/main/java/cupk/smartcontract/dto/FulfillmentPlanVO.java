package cupk.smartcontract.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class FulfillmentPlanVO {
    private Long planId;
    private Long contractId;
    private String contractNo;
    private String contractTitle;
    private String milestoneName;
    private LocalDate dueDate;
    private LocalDate actualDate;
    private String status;
    private String statusName;
    private String completionNotes;

    public static String statusName(String status) {
        return switch (status) {
            case "PENDING" -> "待处理";
            case "PROCESSING", "IN_PROGRESS" -> "进行中";
            case "FULFILLED", "COMPLETED" -> "已完成";
            case "OVERDUE" -> "已逾期";
            default -> status;
        };
    }
}
