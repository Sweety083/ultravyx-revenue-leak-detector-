package com.ultravyx.leaks.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ultravyx.leaks")
public record LeakProperties(Duration uncontacted, Duration slowResponse, Duration qualifiedNotProgressed) {}
