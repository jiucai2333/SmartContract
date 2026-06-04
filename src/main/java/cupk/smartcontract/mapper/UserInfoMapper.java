package cupk.smartcontract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cupk.smartcontract.entity.UserInfo;
import cupk.smartcontract.dto.RoleVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 通过 user_info.role_id 查询用户角色信息（单角色体系）。
     */
    @Select("""
            SELECT r.role_code, r.role_name
            FROM role_info r
            INNER JOIN user_info u ON u.role_id = r.role_id
            WHERE u.user_id = #{userId}
              AND r.is_deleted = 0
            LIMIT 1
            """)
    RoleVO selectRoleByUserId(@Param("userId") Long userId);

    @Select("SELECT role_id FROM role_info WHERE role_code = #{roleCode} AND is_deleted = 0 LIMIT 1")
    Long selectRoleIdByCode(@Param("roleCode") String roleCode);

    /**
     * 设置用户的唯一角色（通过 role_id）。
     */
    @Update("UPDATE user_info SET role_id = #{roleId} WHERE user_id = #{userId}")
    void updateUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 清除用户角色。
     */
    @Update("UPDATE user_info SET role_id = NULL WHERE user_id = #{userId}")
    void clearUserRole(@Param("userId") Long userId);

    @Select("SELECT r.role_code, r.role_name FROM role_info r WHERE r.is_deleted = 0 ORDER BY r.role_id")
    List<RoleVO> selectAllRoles();
}
