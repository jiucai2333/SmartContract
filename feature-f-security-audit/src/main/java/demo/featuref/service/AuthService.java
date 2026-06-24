package demo.featuref.service;

import demo.featuref.common.RoleCode;
import demo.featuref.dto.AuthUserVO;
import demo.featuref.dto.RoleVO;
import demo.featuref.model.DemoUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Service
public class AuthService {
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AtomicLong idGenerator = new AtomicLong(1000);
    private final Map<String, DemoUser> usersByName = new ConcurrentHashMap<>();

    public AuthService() {
        seed("admin", "Admin@123", RoleCode.ADMIN, 1L);
        seed("legal", "Legal@123", RoleCode.LEGAL, 1L);
        seed("leader", "Leader@123", RoleCode.DEPT_LEADER, 2L);
        seed("user", "User@123", RoleCode.USER, 2L);
    }

    public AuthUserVO login(String username, String password) {
        DemoUser user = usersByName.get(normalize(username));
        if (user == null || !user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("invalid username or password");
        }
        return toAuthUser(user);
    }

    public AuthUserVO register(String username, String password) {
        RoleCode roleCode = RoleCode.USER;
        String key = normalize(username);
        DemoUser created = new DemoUser(
                idGenerator.incrementAndGet(),
                username.trim(),
                passwordEncoder.encode(password),
                1L,
                roleCode,
                true
        );
        DemoUser existing = usersByName.putIfAbsent(key, created);
        if (existing != null) {
            throw new IllegalArgumentException("username already exists");
        }
        return toAuthUser(created);
    }

    public AuthUserVO updateRole(Long userId, RoleCode roleCode) {
        DemoUser user = usersByName.values().stream()
                .filter(item -> item.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        DemoUser updated = user.withRole(roleCode);
        usersByName.put(normalize(user.username()), updated);
        return toAuthUser(updated);
    }

    public List<AuthUserVO> listUsers() {
        return usersByName.values().stream()
                .sorted(Comparator.comparing(DemoUser::userId))
                .map(this::toAuthUser)
                .toList();
    }

    public List<RoleVO> listRoles() {
        return Stream.of(RoleCode.values())
                .map(role -> new RoleVO(role, role.label(), role.dataScope()))
                .toList();
    }

    public AuthUserVO toAuthUser(DemoUser user) {
        return AuthUserVO.of(user.userId(), user.username(), user.deptId(), user.roleCode());
    }

    private void seed(String username, String password, RoleCode roleCode, Long deptId) {
        DemoUser user = new DemoUser(
                idGenerator.incrementAndGet(),
                username,
                passwordEncoder.encode(password),
                deptId,
                roleCode,
                true
        );
        usersByName.put(normalize(username), user);
    }

    private String normalize(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
