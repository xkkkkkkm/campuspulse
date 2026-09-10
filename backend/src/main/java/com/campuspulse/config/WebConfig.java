package com.campuspulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.upload.root:./runtime/uploads}") private String uploadRoot;
    @Value("${app.cors.allowed-origins:http://127.0.0.1:8125,http://localhost:8125}") private String allowedOrigins;
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        for(String kind: new String[]{"avatars","covers"}) {
            String location=Path.of(uploadRoot).resolve(kind).toAbsolutePath().normalize().toUri().toString();
            registry.addResourceHandler("/uploads/"+kind+"/**").addResourceLocations(location.endsWith("/")?location:location+"/").setCachePeriod(300);
        }
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toArray(String[]::new))
                .allowedMethods("GET","POST","PUT","DELETE","PATCH","OPTIONS")
                .allowedHeaders("Authorization","Content-Type","Accept-Language","X-Request-ID","Idempotency-Key")
                .exposedHeaders("X-Request-ID").allowCredentials(false).maxAge(3600);
    }
}
