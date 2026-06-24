package cupk.smartcontract.service;

import cupk.smartcontract.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path baseDir;

    public FileStorageService(StorageProperties storageProperties) throws IOException {
        this.baseDir = Path.of(storageProperties.resolvedBaseDir()).toAbsolutePath().normalize();
        Files.createDirectories(this.baseDir);
    }

    public StoredFile store(MultipartFile file) throws IOException {
        String sha256 = sha256(file.getInputStream());
        String ext = extension(file.getOriginalFilename());
        String objectKey = LocalDate.now() + "/" + sha256.substring(0, 8) + "/" + UUID.randomUUID() + ext;
        Path target = baseDir.resolve(objectKey.replace('/', java.io.File.separatorChar));
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        return new StoredFile(objectKey, sha256, detectType(file.getOriginalFilename(), file.getContentType()), file.getSize());
    }

    public StoredFile store(byte[] bytes, String filename, String fileType) throws IOException {
        String sha256 = sha256(new ByteArrayInputStream(bytes));
        String ext = extension(filename);
        String objectKey = LocalDate.now() + "/" + sha256.substring(0, 8) + "/" + UUID.randomUUID() + ext;
        Path target = baseDir.resolve(objectKey.replace('/', java.io.File.separatorChar));
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return new StoredFile(objectKey, sha256, fileType, bytes.length);
    }

    public Path resolve(String objectKey) {
        Path resolved = baseDir.resolve(objectKey.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("闈炴硶鏂囦欢璺緞");
        }
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("鏂囦欢涓嶅瓨鍦?");
        }
        return resolved;
    }

    private String sha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
                dis.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IOException("计算文件哈希失败", ex);
        }
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private String detectType(String filename, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            if (contentType.contains("pdf")) return "pdf";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
            if (contentType.contains("png")) return "png";
        }
        String ext = extension(filename);
        return ext.isBlank() ? "bin" : ext.substring(1);
    }

    public record StoredFile(String objectKey, String sha256, String fileType, long size) {
    }
}
