package cupk.smartcontract.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.ocr")
public record OcrProperties(
        String apiKey,
        String appCode,
        String endpoint,
        int timeoutSeconds,
        boolean enabled,
        String provider,
        String paddleEndpoint,
        int paddleTimeoutSeconds,
        String accessKeyId,
        String accessKeySecret,
        String openapiEndpoint,
        String paddleToken,
        String paddleJobUrl
) {
    public String resolvedProvider() {
        return provider == null || provider.isBlank() ? "paddle" : provider.trim().toLowerCase();
    }

    public String resolvedPaddleToken() {
        return paddleToken == null || paddleToken.isBlank() ? "" : paddleToken.trim();
    }

    public String resolvedPaddleJobUrl() {
        return paddleJobUrl == null || paddleJobUrl.isBlank()
                ? "https://paddleocr.aistudio-app.com/api/v2/ocr/jobs"
                : paddleJobUrl.trim();
    }

    public String resolvedEndpoint() {
        return endpoint == null || endpoint.isBlank()
                ? "https://ocrapi-document.high.aliyuncs.com"
                : endpoint.trim();
    }

    public String resolvedOpenapiEndpoint() {
        return openapiEndpoint == null || openapiEndpoint.isBlank()
                ? "ocr-api.cn-hangzhou.aliyuncs.com"
                : openapiEndpoint.trim();
    }

    public int resolvedTimeoutSeconds() {
        return timeoutSeconds <= 0 ? 30 : timeoutSeconds;
    }

    public String resolvedPaddleEndpoint() {
        return paddleEndpoint == null || paddleEndpoint.isBlank()
                ? "http://localhost:8866/predict/ocr_system"
                : paddleEndpoint.trim();
    }

    public int resolvedPaddleTimeoutSeconds() {
        return paddleTimeoutSeconds <= 0 ? 120 : paddleTimeoutSeconds;
    }

    public boolean hasOpenapiCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && accessKeySecret != null && !accessKeySecret.isBlank();
    }
}
