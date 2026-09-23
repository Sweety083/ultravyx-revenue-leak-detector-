package com.ultravyx.leaks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UltravyxApplication {
    public static void main(String[] args) { SpringApplication.run(UltravyxApplication.class, args); }
}
