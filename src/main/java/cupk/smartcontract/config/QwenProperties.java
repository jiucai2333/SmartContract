package cupk.smartcontract.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.qwen")
public record QwenProperties(
        String apiKey,
        String baseUrl,
        String model,
        String visionModel,
        Boolean enableThinking,
        Integer thinkingBudget,
        int timeoutSeconds,
        boolean enabled
) {
    public String resolvedBaseUrl() {
        String base = baseUrl == null || baseUrl.isBlank()
                ? "https://dashscope.aliyuncs.com/compatible-mode/v1"
                : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    public String resolvedModel() {
        return model == null || model.isBlank() ? "qwen3-vl-plus" : model;
    }

    public String resolvedVisionModel() {
        return visionModel == null || visionModel.isBlank() ? "qwen3-vl-plus" : visionModel;
    }

    public boolean resolvedEnableThinking() {
        return enableThinking == null || enableThinking;
    }

    public int resolvedThinkingBudget() {
        return thinkingBudget == null || thinkingBudget <= 0 ? 81920 : thinkingBudget;
    }

    public int resolvedTimeoutSeconds() {
        return timeoutSeconds <= 0 ? 180 : timeoutSeconds;
    }

    public long resolvedSseTimeoutMs() {
        return resolvedTimeoutSeconds() * 1000L + 15_000L;
    }
}
