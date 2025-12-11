package com.example.uniactivity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadService {
    
    // Use absolute path to src/main/resources/uploads
    private Path getUploadPath(String subfolder) {
        String basePath = System.getProperty("user.dir");
        return Paths.get(basePath, "src", "main", "resources", "uploads", subfolder);
    }
    
    /**
     * Upload multiple evidence images
     * @param files List of files to upload
     * @return List of relative paths to uploaded files
     */
    public List<String> uploadEvidenceImages(MultipartFile[] files) throws IOException {
        List<String> uploadedPaths = new ArrayList<>();
        
        // Create upload directory if not exists
        Path uploadPath = getUploadPath("evidence");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }
        
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String uploadedPath = uploadSingleFile(file, uploadPath, "evidence");
                uploadedPaths.add(uploadedPath);
            }
        }
        
        return uploadedPaths;
    }

    /**
     * Upload activity images
     */
    public List<String> uploadActivityImages(MultipartFile[] files) throws IOException {
        List<String> uploadedPaths = new ArrayList<>();
        
        Path uploadPath = getUploadPath("activities");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }
        
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String uploadedPath = uploadSingleFile(file, uploadPath, "activities");
                uploadedPaths.add(uploadedPath);
            }
        }
        
        return uploadedPaths;
    }
    
    /**
     * Upload a single file
     */
    private String uploadSingleFile(MultipartFile file, Path uploadPath, String subfolder) throws IOException {
        // Generate unique filename with sanitized original name
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString().substring(0, 8) + extension;
        
        // Save file
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        log.info("Uploaded file: {} -> {}", originalFilename, filePath);
        
        // Return URL path for accessing the file
        return "/uploads/" + subfolder + "/" + uniqueFilename;
    }
    
    /**
     * Delete an uploaded file
     */
    public boolean deleteFile(String relativePath) {
        try {
            // Convert URL path to file system path
            String basePath = System.getProperty("user.dir");
            Path filePath = Paths.get(basePath, "src", "main", "resources", relativePath);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted file: {}", filePath);
                return true;
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", relativePath, e);
        }
        return false;
    }
    
    /**
     * Validate file is an image
     */
    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }
    
    /**
     * Get max file size in bytes (5MB default)
     */
    public long getMaxFileSize() {
        return 5 * 1024 * 1024; // 5MB
    }
}
