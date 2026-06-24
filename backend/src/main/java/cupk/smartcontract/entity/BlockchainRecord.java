package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blockchain_record")
public class BlockchainRecord {
    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;
    private Long contractId;
    private Long versionId;
    private String recordType;
    private String summary;
    private String nodeHash;
    private String previousHash;
    private String merkleRoot;
    private String snapshotData;
    private LocalDateTime recordedAt;
    private String createdBy;
    private LocalDateTime createdAt;
}
