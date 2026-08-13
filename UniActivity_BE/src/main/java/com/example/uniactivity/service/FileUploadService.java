package com.example.uniactivity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String PUBLIC_PREFIX = "/uploads/";

    private final Path uploadRoot;

    public FileUploadService(@Value("${app.upload.root}") String uploadRoot) {
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    public List<String> uploadEvidenceImages(MultipartFile[] files) throws IOException {
        return uploadImages(files, "evidence");
    }

    public List<String> uploadActivityImages(MultipartFile[] files) throws IOException {
        return uploadImages(files, "activities");
    }

    private List<String> uploadImages(MultipartFile[] files, String subfolder) throws IOException {
        List<String> uploadedPaths = new ArrayList<>();
        if (files == null) {
            return uploadedPaths;
        }
        Path directory = safeDirectory(subfolder);
        Files.createDirectories(directory);
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            uploadedPaths.add(uploadSingleFile(file, directory, subfolder));
        }
        return uploadedPaths;
    }

    private String uploadSingleFile(
            MultipartFile file, Path directory, String subfolder) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IOException("Tệp vượt quá kích thước cho phép");
        }
        byte[] content = file.getBytes();
        String extension = detectImageExtension(content);
        if (extension == null) {
            throw new IOException("Tệp không phải ảnh JPEG, PNG hoặc WebP hợp lệ");
        }

        String filename = UUID.randomUUID() + extension;
        Path target = directory.resolve(filename).normalize();
        requireInsideRoot(target);
        Files.write(target, content, StandardOpenOption.CREATE_NEW);
        log.info("Stored uploaded image at {}", target);
        return PUBLIC_PREFIX + subfolder + "/" + filename;
    }

    public boolean deleteFile(String relativePath) {
        if (relativePath == null || !relativePath.startsWith(PUBLIC_PREFIX)) {
            return false;
        }
        try {
            String localPath = relativePath.substring(PUBLIC_PREFIX.length());
            Path target = uploadRoot.resolve(localPath).normalize();
            requireInsideRoot(target);
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                log.info("Deleted uploaded file at {}", target);
            }
            return deleted;
        } catch (IOException | SecurityException e) {
            log.warn("Refused or failed to delete uploaded path: {}", e.getMessage());
            return false;
        }
    }

    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            return false;
        }
        try {
            return detectImageExtension(file.getBytes()) != null;
        } catch (IOException e) {
            return false;
        }
    }

    public long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }

    private Path safeDirectory(String subfolder) throws IOException {
        Path directory = uploadRoot.resolve(subfolder).normalize();
        requireInsideRoot(directory);
        return directory;
    }

    private void requireInsideRoot(Path path) throws IOException {
        if (!path.startsWith(uploadRoot)) {
            throw new IOException("Đường dẫn tệp không hợp lệ");
        }
    }

    private String detectImageExtension(byte[] content) {
        if (isPng(content) && canDecode(content)) {
            return ".png";
        }
        if (isJpeg(content) && canDecode(content)) {
            return ".jpg";
        }
        if (isWebp(content)) {
            return ".webp";
        }
        return null;
    }

    private boolean canDecode(byte[] content) {
        try {
            return ImageIO.read(new ByteArrayInputStream(content)) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isPng(byte[] content) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        return startsWith(content, signature);
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff;
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R' && content[1] == 'I'
                && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E'
                && content[10] == 'B' && content[11] == 'P';
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
