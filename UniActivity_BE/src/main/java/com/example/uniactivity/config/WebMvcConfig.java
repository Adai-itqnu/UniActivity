package com.example.uniactivity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String uploadRoot;

    public WebMvcConfig(@Value("${app.upload.root}") String uploadRoot) {
        this.uploadRoot = uploadRoot;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = java.nio.file.Paths.get(uploadRoot)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }
        registry.addResourceHandler("/uploads/activities/**")
                .addResourceLocations(resourceLocation + "activities/");
    }
}
