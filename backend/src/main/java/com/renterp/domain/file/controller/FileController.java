package com.renterp.domain.file.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.file.dto.FileResponse;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.service.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * File uploads (multipart) and downloads. Every endpoint needs a signed-in
 * user; who may read a file is decided in {@link FileService}.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    // ── POST /api/v1/files  (multipart: file, purpose, propertyId?) ───────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam FilePurpose purpose,
            @RequestParam(required = false) UUID propertyId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded", fileService.upload(file, purpose, propertyId)));
    }

    // ── GET /api/v1/files/{id} ───────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileResponse>> metadata(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("File fetched", fileService.metadata(id)));
    }

    // ── GET /api/v1/files/{id}/content ─────────────────────────────────────────
    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID id) {
        FileService.Download d = fileService.download(id);
        StoredFile f = d.file();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .contentLength(f.getSizeBytes())
                // Private documents: never cached by shared caches, never re-sniffed.
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.getId() + extension(f) + "\"")
                .body(new InputStreamResource(d.content()));
    }

    // ── DELETE /api/v1/files/{id} ──────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        fileService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("File deleted"));
    }

    private static String extension(StoredFile f) {
        return switch (f.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}
