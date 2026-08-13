package com.example.uniactivity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_EVIDENCE_FILES = 3;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final String PUBLIC_PREFIX = "/uploads/";

    private final Path uploadRoot;

    public FileUploadService(@Value("${app.upload.root}") String uploadRoot) {
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    public List<String> uploadEvidenceImages(MultipartFile[] files) throws IOException {
        if (files != null && files.length > MAX_EVIDENCE_FILES) {
            throw new IOException("Tối đa 3 ảnh minh chứng");
        }
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

    public Path resolveEvidenceFile(String filename) throws IOException {
        if (filename == null
                || !filename.matches("[0-9a-fA-F-]{36}\\.(?:png|jpg|webp)")) {
            throw new IOException("Tên tệp minh chứng không hợp lệ");
        }
        Path target = uploadRoot.resolve("evidence").resolve(filename).normalize();
        requireInsideRoot(target);
        if (!Files.isRegularFile(target)) {
            throw new IOException("Không tìm thấy tệp minh chứng");
        }
        return target;
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
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content))) {
            if (input == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return hasSafeDimensions(width, height) && reader.read(0) != null;
            } finally {
                reader.dispose();
            }
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
        if (content.length < 25
                || !hasAscii(content, 0, "RIFF")
                || !hasAscii(content, 8, "WEBP")
                || littleEndianUnsignedInt(content, 4) + 8 != content.length) {
            return false;
        }

        long chunkSize = littleEndianUnsignedInt(content, 16);
        if (chunkSize > content.length - 20L) {
            return false;
        }
        if (hasAscii(content, 12, "VP8 ")) {
            return chunkSize >= 10
                    && content.length >= 30
                    && (content[23] & 0xff) == 0x9d
                    && (content[24] & 0xff) == 0x01
                    && (content[25] & 0xff) == 0x2a
                    && hasSafeDimensions(
                            littleEndianUnsignedShort(content, 26) & 0x3fff,
                            littleEndianUnsignedShort(content, 28) & 0x3fff);
        }
        if (hasAscii(content, 12, "VP8L")) {
            if (chunkSize < 5 || (content[20] & 0xff) != 0x2f) {
                return false;
            }
            int bits = (content[21] & 0xff)
                    | ((content[22] & 0xff) << 8)
                    | ((content[23] & 0xff) << 16)
                    | ((content[24] & 0xff) << 24);
            return hasSafeDimensions((bits & 0x3fff) + 1, ((bits >> 14) & 0x3fff) + 1);
        }
        return hasAscii(content, 12, "VP8X") && chunkSize == 10 && content.length >= 30
                && hasSafeDimensions(
                        littleEndianUnsigned24(content, 24) + 1,
                        littleEndianUnsigned24(content, 27) + 1);
    }

    private boolean hasSafeDimensions(int width, int height) {
        return width > 0 && height > 0
                && (long) width * height <= MAX_IMAGE_PIXELS;
    }

    private int littleEndianUnsignedShort(byte[] content, int offset) {
        return (content[offset] & 0xff) | ((content[offset + 1] & 0xff) << 8);
    }

    private int littleEndianUnsigned24(byte[] content, int offset) {
        return (content[offset] & 0xff)
                | ((content[offset + 1] & 0xff) << 8)
                | ((content[offset + 2] & 0xff) << 16);
    }

    private long littleEndianUnsignedInt(byte[] content, int offset) {
        return (content[offset] & 0xffL)
                | ((content[offset + 1] & 0xffL) << 8)
                | ((content[offset + 2] & 0xffL) << 16)
                | ((content[offset + 3] & 0xffL) << 24);
    }

    private boolean hasAscii(byte[] content, int offset, String expected) {
        if (offset < 0 || content.length - offset < expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (content[offset + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
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
