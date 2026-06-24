package cupk.smartcontract.service;

import cupk.smartcontract.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
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

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path baseDir;

    public FileStorageService(StorageProperties storageProperties) throws IOException {
        this.baseDir = resolveBaseDir(storageProperties.resolvedBaseDir());
        log.info("File storage base directory resolved to: {}", this.baseDir);
        Files.createDirectories(this.baseDir);
    }

    /**
     * 解析存储根目录。
     * 绝对路径直接使用；相对路径基于 Maven 模块根（pom.xml 所在目录）解析，
     * 而非 JVM 工作目录（user.dir），这样无论从哪个目录启动应用，数据都会落在项目里。
     */
    private static Path resolveBaseDir(String configured) {
        Path raw = Path.of(configured);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }

        // 以应用 classpath 为锚点，向上查找 pom.xml 定位 Maven 模块根（即 backend/）
        ApplicationHome home = new ApplicationHome(FileStorageService.class);
        Path anchor = home.getDir().toPath(); // IDE: target/classes; JAR: jar 所在目录

        for (Path cursor = anchor; cursor != null; cursor = cursor.getParent()) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                anchor = cursor;
                break;
            }
        }
        // 如果找不到 pom.xml（比如非 Maven 部署），则回退到 classpath 根目录

        return anchor.resolve(raw).normalize().toAbsolutePath();
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

    public void delete(String objectKey) throws IOException {
        Path resolved = baseDir.resolve(objectKey.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid storage object key");
        }
        Files.deleteIfExists(resolved);
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
