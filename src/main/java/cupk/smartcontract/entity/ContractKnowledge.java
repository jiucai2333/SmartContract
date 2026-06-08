package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("contract_knowledge")
public class ContractKnowledge {
    @TableId(value = "knowledge_id", type = IdType.AUTO)
    private Long knowledgeId;
    private Long contractId;
    private Long archiveId;
    private String contractNo;
    private String title;
    private String contractType;
    private BigDecimal amount;
    private LocalDate signDate;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String partyA;
    private String partyB;
    private String keyClauses;
    private String paymentTerms;
    private String keywords;
    private String metadataJson;
    private String fullText;
    private String extractStatus;
    private String extractError;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
