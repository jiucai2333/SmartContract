package cupk.smartcontract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cupk.smartcontract.domain.UserInfo;
import cupk.smartcontract.dto.RoleVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface UserInfoMapper extends BaseMapper<UserInfo> {

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

    @Update("UPDATE user_info SET role_id = #{roleId} WHERE user_id = #{userId}")
    void updateUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Update("UPDATE user_info SET role_id = NULL WHERE user_id = #{userId}")
    void clearUserRole(@Param("userId") Long userId);

    @Select("SELECT r.role_code, r.role_name FROM role_info r WHERE r.is_deleted = 0 ORDER BY r.role_id")
    List<RoleVO> selectAllRoles();
}
