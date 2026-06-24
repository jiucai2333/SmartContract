package cupk.smartcontract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.dto.AuthUserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class TokenService {
    public static final long ACCESS_TTL_SECONDS = 2 * 60 * 60L;
    public static final long REFRESH_TTL_SECONDS = 7 * 24 * 60 * 60L;

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public TokenService(ObjectMapper objectMapper,
                        @Value("${security.jwt.secret:smart-contract-demo-secret-change-me-32-bytes}") String secret) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes");
        }
    }

    public AuthUserVO issue(AuthUserVO user) {
        return user.withTokens(
                createToken(user, ACCESS_TTL_SECONDS, "access"),
                createToken(user, REFRESH_TTL_SECONDS, "refresh"),
                ACCESS_TTL_SECONDS
        );
    }

    private String createToken(AuthUserVO user, long ttlSeconds, String type) {
        try {
            String header = base64Url(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = base64Url(objectMapper.writeValueAsBytes(Map.of(
                    "sub", user.username(),
                    "uid", user.userId(),
                    "did", user.deptId() != null ? user.deptId() : 0L,
                    "role", user.roleCode() != null ? user.roleCode() : "USER",
                    "scope", user.dataScope() != null ? user.dataScope() : "SELF",
                    "type", type,
                    "exp", Instant.now().plusSeconds(ttlSeconds).getEpochSecond()
            )));
            return header + "." + payload + "." + sign(header + "." + payload);
        } catch (Exception ex) {
            throw new IllegalStateException("创建 JWT 失败", ex);
        }
    }

    public Map<String, Object> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), parts[2])) {
                throw new IllegalArgumentException("JWT 签名无效");
            }
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            Number exp = (Number) payload.get("exp");
            if (exp == null || exp.longValue() < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("JWT宸茶繃鏈?");
            }
            return payload;
        } catch (Exception ex) {
            throw new IllegalArgumentException("JWT 无效", ex);
        }
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
