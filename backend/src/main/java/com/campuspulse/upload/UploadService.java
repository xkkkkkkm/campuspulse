package com.campuspulse.upload;

import com.campuspulse.common.Api;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class UploadService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Path root;
    private final long quotaBytes, maxPixels;
    private final Semaphore decoders = new Semaphore(2);

    public UploadService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                         @Value("${app.upload.root:frontend/uploads}") String root,
                         @Value("${app.upload.quota-bytes:104857600}") long quotaBytes,
                         @Value("${app.upload.max-pixels:12000000}") long maxPixels) {
        this.jdbc = jdbc; this.transaction = new TransactionTemplate(manager);
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.quotaBytes = quotaBytes; this.maxPixels = maxPixels;
    }

    public String save(long userId, MultipartFile file, String kind, long maxBytes) throws IOException {
        if (file == null || file.isEmpty()) throw bad("文件为空");
        if (file.getSize() > maxBytes) throw new Api.ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "图片超过大小限制");
        if (!Set.of("avatars", "covers", "chat").contains(kind)) throw bad("参数错误");
        if (!decoders.tryAcquire()) throw new Api.ApiException(HttpStatus.TOO_MANY_REQUESTS, Api.localize("图片处理繁忙，请稍后重试", "Image processing is busy. Try again shortly."));
        Path destination = null;
        try {
            Files.createDirectories(root);
            Path realRoot = root.toRealPath();
            String id = UUID.randomUUID().toString().replace("-", "");
            String relative = kind.equals("chat") ? "chat/"+id+".png" : kind + "/" + LocalDate.now().toString().replace("-", "") + "/" + id + ".png";
            destination = realRoot.resolve(relative).normalize();
            Files.createDirectories(destination.getParent());
            if (!destination.getParent().toRealPath().startsWith(realRoot)) throw bad("非法文件名");
            try (InputStream input = file.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
                if (imageInput == null) throw bad("文件内容不是有效图片");
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw bad("文件内容不是有效图片");
                ImageReader reader = readers.next();
                try {
                    if (!Set.of("PNG", "JPEG", "JPG", "GIF").contains(reader.getFormatName().toUpperCase(Locale.ROOT))) throw bad("文件内容不是有效图片");
                    reader.setInput(imageInput, true, true);
                    validateDimensions(reader.getWidth(0), reader.getHeight(0), maxPixels);
                    BufferedImage decoded = reader.read(0); // Only after bounded header inspection; no animation or metadata preserved.
                    if (decoded == null) throw bad("文件内容不是有效图片");
                    try (OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                        if (!ImageIO.write(decoded, "png", output)) throw bad("文件内容不是有效图片");
                    } finally { decoded.flush(); }
                } finally { reader.dispose(); }
            } catch (javax.imageio.IIOException e) { throw bad("文件内容不是有效图片"); }
            long size = Files.size(destination);
            String url = kind.equals("chat") ? "/api/chat-media/"+id : "/uploads/" + relative;
            transaction.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", userId);
                Long used = jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes), 0) FROM uploaded_file WHERE user_id = ?", Long.class, userId);
                if (size > quotaBytes - (used == null ? 0 : used)) throw new Api.ApiException(HttpStatus.PAYLOAD_TOO_LARGE, Api.localize("已达到图片存储配额，请删除未使用图片", "Image storage quota reached. Delete unused uploads."));
                jdbc.update("INSERT INTO uploaded_file (id, user_id, kind, url, size_bytes) VALUES (?, ?, ?, ?, ?)", id, userId, kind, url, size);
            });
            return url;
        } catch (IOException | RuntimeException ex) {
            if (destination != null) Files.deleteIfExists(destination);
            throw ex;
        } finally { decoders.release(); }
    }

    static void validateDimensions(int width, int height, long maxPixels) {
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > maxPixels)
            throw new Api.ApiException(HttpStatus.PAYLOAD_TOO_LARGE, Api.localize("图片尺寸过大", "Image dimensions exceed the allowed limit."));
    }

    public void requireOwned(long userId, String url, String kind) {
        if (url == null || url.isBlank()) return;
        if (url.length() > 512 || url.contains("\\") || url.chars().anyMatch(Character::isISOControl)) throw bad("非法图片地址");
        if (url.startsWith("/uploads/")) {
            var owned = jdbc.queryForList("SELECT id FROM uploaded_file WHERE user_id = ? AND url = ? AND kind = ? FOR UPDATE", userId, url, kind);
            if (owned.size() != 1) throw new Api.ApiException(HttpStatus.FORBIDDEN, Api.localize("只能引用本人上传的图片", "Only your own uploaded images may be used."));
            return;
        }
        try {
            URI parsed = URI.create(url);
            if ("https".equalsIgnoreCase(parsed.getScheme()) && parsed.getHost() != null && parsed.getUserInfo() == null) return;
            if (!parsed.isAbsolute() && parsed.getRawAuthority() == null && url.matches("/?assets/[A-Za-z0-9_./-]+") && !url.contains("..")) return;
        } catch (IllegalArgumentException ignored) { }
        throw bad("非法图片地址");
    }

    public List<Map<String, Object>> list(long userId) {
        return jdbc.queryForList("SELECT id, kind, url, size_bytes AS sizeBytes, created_at AS createdAt FROM uploaded_file WHERE user_id = ? ORDER BY created_at DESC LIMIT 200", userId);
    }

    public void deleteUnused(long userId, String id) {
        transaction.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", userId);
            var files = jdbc.queryForList("SELECT url FROM uploaded_file WHERE id = ? AND user_id = ? FOR UPDATE", id, userId);
            if (files.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND, "文件不存在");
            String url = String.valueOf(files.get(0).get("url"));
            Long references = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM users WHERE avatar_url = ?) + (SELECT COUNT(*) FROM activities WHERE cover_url = ?) + (SELECT COUNT(*) FROM messages WHERE image_url=?) + (SELECT COUNT(*) FROM activity_chat_message WHERE image_url=?) + (SELECT COUNT(*) FROM dm_message WHERE image_url=?)", Long.class, url, url, url, url, url);
            if (references != null && references > 0) throw new Api.ApiException(HttpStatus.CONFLICT, Api.localize("图片仍在使用中", "This image is still in use."));
            Path target = url.startsWith("/api/chat-media/") ? root.resolve("chat/"+id+".png").normalize() : root.resolve(url.substring("/uploads/".length())).normalize();
            if (!target.startsWith(root)) throw bad("非法文件名");
            try { Files.deleteIfExists(target); } catch (IOException e) { throw new IllegalStateException(e); }
            jdbc.update("DELETE FROM uploaded_file WHERE id = ? AND user_id = ?", id, userId);
        });
    }
    private static Api.ApiException bad(String message) { return new Api.ApiException(HttpStatus.BAD_REQUEST, message); }
}
