package demo.featuref.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.featuref.common.RoleCode;
import demo.featuref.dto.AuthUserVO;
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

    public TokenService(
            ObjectMapper objectMapper,
            @Value("${featuref.jwt.secret}") String secret
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("featuref.jwt.secret must be at least 32 bytes");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public AuthUserVO issue(AuthUserVO user) {
        return user.withTokens(
                createToken(user, ACCESS_TTL_SECONDS, "access"),
                createToken(user, REFRESH_TTL_SECONDS, "refresh"),
                ACCESS_TTL_SECONDS
        );
    }

    public Map<String, Object> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), parts[2])) {
                throw new IllegalArgumentException("invalid JWT signature");
            }
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {
                    });
            Number exp = (Number) payload.get("exp");
            if (exp == null || exp.longValue() < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("expired JWT");
            }
            return payload;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid JWT", ex);
        }
    }

    private String createToken(AuthUserVO user, long ttlSeconds, String type) {
        try {
            String header = base64Url(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = base64Url(objectMapper.writeValueAsBytes(Map.of(
                    "sub", user.username(),
                    "uid", user.userId(),
                    "did", user.deptId() == null ? 0L : user.deptId(),
                    "role", user.roleCode() == null ? RoleCode.USER.name() : user.roleCode().name(),
                    "scope", user.dataScope().name(),
                    "type", type,
                    "exp", Instant.now().plusSeconds(ttlSeconds).getEpochSecond()
            )));
            return header + "." + payload + "." + sign(header + "." + payload);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create JWT", ex);
        }
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
