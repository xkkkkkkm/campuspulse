package com.campuspulse.upload;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    private final UploadService service;
    private final long maxAvatarBytes, maxCoverBytes;
    public UploadController(UploadService service,
                            @Value("${app.upload.max-avatar-bytes:5242880}") long maxAvatarBytes,
                            @Value("${app.upload.max-cover-bytes:10485760}") long maxCoverBytes) {
        this.service = service; this.maxAvatarBytes = maxAvatarBytes; this.maxCoverBytes = maxCoverBytes;
    }
    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Api.ApiResponse<Map<String,Object>> avatar(@RequestPart("file") MultipartFile file) throws IOException {
        return Api.ok(Map.of("url", service.save(AuthContext.requireUser().id(), file, "avatars", maxAvatarBytes)));
    }
    @PostMapping(path = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Api.ApiResponse<Map<String,Object>> cover(@RequestPart("file") MultipartFile file) throws IOException {
        return Api.ok(Map.of("url", service.save(AuthContext.requireUser().id(), file, "covers", maxCoverBytes)));
    }
    @PostMapping(path="/chat", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Api.ApiResponse<Map<String,Object>> chat(@RequestPart("file") MultipartFile file) throws IOException {
        return Api.ok(Map.of("url",service.save(AuthContext.requireUser().id(),file,"chat",maxCoverBytes)));
    }
    @GetMapping("/files")
    public Api.ApiResponse<List<Map<String,Object>>> files() { return Api.ok(service.list(AuthContext.requireUser().id())); }
    @DeleteMapping("/files/{id}")
    public Api.ApiResponse<Void> delete(@PathVariable String id) { service.deleteUnused(AuthContext.requireUser().id(), id); return Api.ok(); }
}
