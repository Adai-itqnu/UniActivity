package com.example.uniactivity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileUploadServiceTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path tempDirectory;

    private Path uploadRoot;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        uploadRoot = tempDirectory.resolve("uploads");
        service = new FileUploadService(uploadRoot.toString());
    }

    @Test
    void rejectsSpoofedImageContentTypeAndSvg() {
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "attack.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes());
        MockMultipartFile svg = new MockMultipartFile(
                "file", "attack.svg", "image/svg+xml", "<svg onload='alert(1)'></svg>".getBytes());

        assertFalse(service.isValidImage(spoofed));
        assertFalse(service.isValidImage(svg));
    }

    @Test
    void rejectsRiffFileThatIsNotAValidWebpBitstream() {
        byte[] fakeWebp = {
                'R', 'I', 'F', 'F', 12, 0, 0, 0,
                'W', 'E', 'B', 'P', 'J', 'U', 'N', 'K',
                0, 0, 0, 0
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.webp", "image/webp", fakeWebp);

        assertFalse(service.isValidImage(file));
    }

    @Test
    void rejectsOversizedFilesAtTheStorageBoundary() {
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.png", "image/png",
                new byte[(int) service.getMaxFileSize() + 1]);

        assertThrows(java.io.IOException.class,
                () -> service.uploadEvidenceImages(new MockMultipartFile[]{oversized}));
    }

    @Test
    void rejectsMoreThanThreeEvidenceFilesAtTheStorageBoundary() {
        MockMultipartFile png = new MockMultipartFile(
                "file", "image.png", "image/png", ONE_PIXEL_PNG);

        assertThrows(java.io.IOException.class,
                () -> service.uploadEvidenceImages(
                        new MockMultipartFile[]{png, png, png, png}));
    }

    @Test
    void ignoresTraversalFilenameAndUsesDetectedExtension() throws Exception {
        MockMultipartFile png = new MockMultipartFile(
                "file", "../../outside.html", "text/html", ONE_PIXEL_PNG);

        List<String> urls = service.uploadEvidenceImages(new MockMultipartFile[]{png});

        assertTrue(urls.get(0).matches("/uploads/evidence/[0-9a-f-]+\\.png"));
        try (var paths = Files.list(uploadRoot.resolve("evidence"))) {
            assertTrue(paths.allMatch(path -> path.normalize().startsWith(uploadRoot.normalize())));
        }
        assertFalse(Files.exists(tempDirectory.resolve("outside.html")));
    }

    @Test
    void refusesDeleteTraversalOutsideUploadRoot() throws Exception {
        Path outside = tempDirectory.resolve("secret.txt");
        Files.writeString(outside, "keep");

        assertFalse(service.deleteFile("/uploads/../secret.txt"));
        assertTrue(Files.exists(outside));
    }
}
