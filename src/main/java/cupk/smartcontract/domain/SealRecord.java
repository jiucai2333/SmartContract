package cupk.smartcontract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seal_record")
public class SealRecord extends BaseAuditEntity {
    @TableId(value = "seal_id", type = IdType.AUTO)
    private Long sealId;
    private Long contractId;
    private Long versionId;
    private Long fileId;
    private String fileUrl;
    private String fileName;
    private String sealStatus;
    private LocalDateTime sealTime;
    private Long operatorId;
    private String remark;
}
