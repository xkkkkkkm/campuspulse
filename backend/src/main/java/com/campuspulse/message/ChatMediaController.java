package com.campuspulse.message;

import com.campuspulse.common.Api;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.io.IOException;

/** Attachments are private API resources; public upload handlers never map the chat directory. */
@RestController
public class ChatMediaController {
    private final MessageService messages;
    private final Path root;
    public ChatMediaController(MessageService messages,@Value("${app.upload.root:./runtime/uploads}") String root) {this.messages=messages;this.root=Path.of(root).toAbsolutePath().normalize();}
    @GetMapping("/api/chat-media/{id}")
    public ResponseEntity<Resource> read(@PathVariable String id) throws IOException {
        if(!id.matches("[a-f0-9]{32}")) throw new Api.ApiException(HttpStatus.NOT_FOUND,"Image not found");
        messages.requireMediaAccess(id);
        Path path=root.resolve("chat/"+id+".png");
        if(!Files.isRegularFile(path) || !path.toRealPath().startsWith(root.toRealPath())) throw new Api.ApiException(HttpStatus.NOT_FOUND,"Image not found");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));
    }
}
