package cupk.smartcontract.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 电子签章配置属性。
 * 通过 application.properties 中的 signature.* 前缀注入。
 */
@ConfigurationProperties(prefix = "signature")
public record SignatureProperties(
        String provider,
        String fadadaApiUrl,
        String fadadaAppId,
        String fadadaAppSecret,
        String fadadaCallbackUrl,
        String fadadaCorpId
) {
    /**
     * 返回解析后的 provider 名称，默认为 "local"。
     */
    public String resolvedProvider() {
        return provider == null || provider.isBlank() ? "local" : provider.trim().toLowerCase();
    }

    /**
     * 返回解析后的回调地址，未配置时返回空字符串。
     */
    public String resolvedCallbackUrl() {
        return fadadaCallbackUrl == null ? "" : fadadaCallbackUrl.trim();
    }
}
