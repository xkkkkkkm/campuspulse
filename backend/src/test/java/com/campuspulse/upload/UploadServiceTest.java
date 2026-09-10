package com.campuspulse.upload;
import com.campuspulse.common.Api;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UploadServiceTest {
    @TempDir Path directory;
    @Test void boundsPixelCountBeforeDecodeAndPreventsIntegerOverflow() {
        assertThrows(Api.ApiException.class, () -> UploadService.validateDimensions(8192,8192,12000000));
        assertThrows(Api.ApiException.class, () -> UploadService.validateDimensions(Integer.MAX_VALUE,2,12000000));
        assertDoesNotThrow(() -> UploadService.validateDimensions(3000,3000,12000000));
    }
    @Test void rejectsHtmlMasqueradingAsPngAndUnsafeReferences() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UploadService service = new UploadService(jdbc, mock(PlatformTransactionManager.class), directory.toString(), 1000, 100000);
        assertThrows(Api.ApiException.class, () -> service.save(1,new MockMultipartFile("file","cover.png","image/png","<script>alert(1)</script>".getBytes()),"covers",1024));
        for (String url : List.of("javascript:alert(1)","data:image/svg+xml,a","//evil.example/a.png","/assets/../../private"))
            assertThrows(Api.ApiException.class, () -> service.requireOwned(1,url,"covers"));
        when(jdbc.queryForList(anyString(),eq(1L),eq("/uploads/covers/foreign.png"),eq("covers"))).thenReturn(List.of());
        assertThrows(Api.ApiException.class, () -> service.requireOwned(1,"/uploads/covers/foreign.png","covers"));
    }
}
