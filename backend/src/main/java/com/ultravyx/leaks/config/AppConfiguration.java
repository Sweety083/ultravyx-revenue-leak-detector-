package com.ultravyx.leaks.config;

import java.time.Clock;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean WebMvcConfigurer corsConfigurer(@Value("${ultravyx.cors-origin}") String origin) {
        String[] origins = Arrays.stream(origin.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toArray(String[]::new);
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "OPTIONS");
            }
        };
    }
}
