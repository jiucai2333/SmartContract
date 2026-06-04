package cupk.smartcontract.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.local")
public record StorageProperties(
        String baseDir,
        String bucket
) {
    public String resolvedBaseDir() {
        return baseDir == null || baseDir.isBlank() ? "./data/uploads" : baseDir;
    }

    public String resolvedBucket() {
        return bucket == null || bucket.isBlank() ? "local" : bucket;
    }
}
