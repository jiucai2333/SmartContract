package demo.featuref.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration")
public record ExternalIntegrationProperties(
        Endpoint oa,
        Endpoint hr,
        Endpoint electronicSignature
) {
    public record Endpoint(
            boolean enabled,
            String baseUrl,
            String appKey,
            String appSecret,
            int timeoutSeconds
    ) {
        public int resolvedTimeoutSeconds() {
            return timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        }
    }
}
