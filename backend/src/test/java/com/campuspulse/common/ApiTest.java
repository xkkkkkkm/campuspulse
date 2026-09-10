package com.campuspulse.common;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ApiTest {
    @AfterEach void clear() { LocaleContextHolder.resetLocaleContext(); }
    @Test void errorsKeepStableCodeAndSelectedLanguage() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var english=Api.failure("活动名额已满",HttpStatus.CONFLICT);
        assertEquals("CONFLICT",english.code()); assertEquals("The activity is full.",english.message());
        assertEquals("Password must contain at most 128 characters.",Api.message("密码长度不能超过 128",HttpStatus.BAD_REQUEST));
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        var chinese=Api.failure("活动名额已满",HttpStatus.CONFLICT);
        assertEquals(english.code(),chinese.code()); assertEquals("活动名额已满",chinese.message());
    }
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
            .setControllerAdvice(new Api.GlobalExceptionHandler()).build();

    @Test void missingResourcesAndRoutesReturn404WithoutExposingPaths() throws Exception {
        mvc.perform(get("/resource").header("Accept-Language", "en"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
        mvc.perform(get("/no-such-route"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test void methodMediaAndBindingErrorsKeepClientStatus() throws Exception {
        mvc.perform(post("/resource"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/json").contentType("text/plain").content("invalid"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/required"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @RestController
    static class ErrorTestController {
        @GetMapping("/resource") String resource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "private/path.png");
        }
        @PostMapping(value = "/json", consumes = "application/json") String json(@RequestBody String value) { return value; }
        @GetMapping("/required") String required(@RequestParam("id") String id) { return id; }
    }
}
