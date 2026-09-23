package com.ultravyx.leaks.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigurationTest {
    private static class Registry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() { return getCorsConfigurations(); }
    }
    private CorsConfiguration config(String origins) {
        Registry registry = new Registry();
        new AppConfiguration().corsConfigurer(origins).addCorsMappings(registry);
        return registry.configurations().get("/api/**");
    }
    @Test void allowsBrowserUploadsFromBothLocalFrontendAddresses() throws Exception {
        CorsConfiguration config = config("http://localhost:4200, http://127.0.0.1:4200");
        for (String origin : new String[] {"http://localhost:4200", "http://127.0.0.1:4200"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/leads/upload");
            request.setServerName("127.0.0.1");
            request.setServerPort(8080);
            request.addHeader("Origin", origin);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertTrue(new DefaultCorsProcessor().processRequest(config, request, response));
            assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
        }
    }
    @Test void unrelatedOriginsRemainRestrictedAndCustomOriginCanReplaceDefaults() {
        assertNull(config("http://localhost:4200,http://127.0.0.1:4200").checkOrigin("https://other.example"));
        CorsConfiguration custom = config("https://demo.example");
        assertEquals("https://demo.example", custom.checkOrigin("https://demo.example"));
        assertNull(custom.checkOrigin("http://localhost:4200"));
    }
}
