package com.stockpulse.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * The UI calls the API same-origin through its dev-server proxy, so CORS only matters
     * for a frontend hosted on a different origin. Comma-separated list, never "*".
     */
    @Value("${app.cors.allowed-origins:http://localhost:3010}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-API-Key")
                .allowCredentials(true);
    }
}
