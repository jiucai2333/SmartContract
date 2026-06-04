package cupk.smartcontract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_info")
public class UserInfo extends BaseAuditEntity {
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    private Long deptId;
    private Long roleId;
    private String username;
    private String password;
    private Integer status;
}
