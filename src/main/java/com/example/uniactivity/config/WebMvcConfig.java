package com.example.uniactivity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded files from src/main/resources/uploads
        String basePath = System.getProperty("user.dir").replace("\\", "/");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:///" + basePath + "/src/main/resources/uploads/");
    }
}
