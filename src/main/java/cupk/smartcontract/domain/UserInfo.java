package cupk.smartcontract.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

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
    private String mobile;
    private String email;
    private Integer status;
}
