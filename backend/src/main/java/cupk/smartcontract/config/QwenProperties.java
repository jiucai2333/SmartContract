package cupk.smartcontract.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.qwen")
public record QwenProperties(
        String apiKey,
        String baseUrl,
        String model,
        String visionModel,
        String layoutModel,
        int timeoutSeconds,
        boolean enabled,
        boolean enableThinking,
        int thinkingBudget
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
        return model == null || model.isBlank() ? "qwen-plus" : model;
    }

    public String resolvedVisionModel() {
        return visionModel == null || visionModel.isBlank() ? "qwen-vl-plus" : visionModel;
    }

    public String resolvedLayoutModel() {
        return layoutModel == null || layoutModel.isBlank() ? resolvedModel() : layoutModel;
    }

    public int resolvedTimeoutSeconds() {
        return timeoutSeconds <= 0 ? 120 : timeoutSeconds;
    }

    public long resolvedSseTimeoutMs() {
        return resolvedTimeoutSeconds() * 1000L + 15_000L;
    }

    public boolean resolvedEnableThinking() {
        return enableThinking;
    }

    public int resolvedThinkingBudget() {
        return thinkingBudget <= 0 ? 0 : thinkingBudget;
    }
}
