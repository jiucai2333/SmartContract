package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.common.RoleEnum;
import cupk.smartcontract.entity.UserInfo;
import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.dto.RoleVO;
import cupk.smartcontract.dto.UserAdminVO;
import cupk.smartcontract.mapper.UserInfoMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private static final long DEFAULT_DEPT_ID = 1L;
    private static final String DEFAULT_ROLE = "USER";

    private final UserInfoMapper userMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserInfoMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional
    public AuthUserVO login(String username, String password) {
        String normalized = username.trim();
        UserInfo user = findByUsername(normalized);
        if (user == null) {
            return null;
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return AuthUserVO.wrongPassword();
        }
        return toAuthUser(user);
    }

    @Transactional
    public AuthUserVO register(String username, String password) {
        String normalized = username.trim();
        if (findByUsername(normalized) != null) {
            throw new RuntimeException("用户名已存在");
        }
        return toAuthUser(createUser(normalized, password, DEFAULT_ROLE, "register"));
    }

    private UserInfo createUser(String username, String password, String roleCode, String createdBy) {
        UserInfo user = new UserInfo();
        user.setDeptId(DEFAULT_DEPT_ID);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        user.setCreatedBy(createdBy);
        user.setVersion(1);

        // 璁剧疆鍞竴瑙掕壊
        String normalizedRole = roleCode.trim().toUpperCase();
        Long roleId = userMapper.selectRoleIdByCode(normalizedRole);
        user.setRoleId(roleId);

        userMapper.insert(user);
        return user;
    }

    private UserInfo findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getUsername, username.trim())
                .last("LIMIT 1"));
    }

    private AuthUserVO toAuthUser(UserInfo user) {
        RoleVO roleVO = userMapper.selectRoleByUserId(user.getUserId());
        String roleCode = (roleVO != null && roleVO.roleCode() != null)
                ? roleVO.roleCode().trim().toUpperCase()
                : DEFAULT_ROLE;

        RoleEnum roleEnum = RoleEnum.fromCode(roleCode);
        String dataScope = (roleEnum != null)
                ? roleEnum.getDataScope()
                : RoleEnum.USER.getDataScope();

        return AuthUserVO.of(user.getUserId(), user.getUsername(),
                user.getDeptId(), roleCode, dataScope);
    }

    public java.util.List<UserAdminVO> listUsers() {
        return userMapper.selectAdminUsers();
    }

    @Transactional
    public AuthUserVO updateUserRole(Long userId, String roleCode) {
        UserInfo user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在?");

        String normalizedRole = roleCode.trim().toUpperCase();
        Long roleId = userMapper.selectRoleIdByCode(normalizedRole);
        if (roleId == null) throw new IllegalArgumentException("无效的角色编码? " + roleCode);

        userMapper.updateUserRole(userId, roleId);
        // ??鏇存柊鍐呭瓨涓殑 role_id 浠ヤ究 toAuthUser 鑳借鍒?
        user.setRoleId(roleId);
        return toAuthUser(user);
    }

    public java.util.List<RoleVO> listRoles() {
        return userMapper.selectAllRoles();
    }
}
