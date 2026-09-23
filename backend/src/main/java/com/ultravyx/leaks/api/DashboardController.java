package com.ultravyx.leaks.api;

import com.ultravyx.leaks.api.ApiDtos.Funnel;
import com.ultravyx.leaks.api.ApiDtos.Summary;
import com.ultravyx.leaks.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/dashboard")
public class DashboardController {
    private final AnalyticsService analytics;
    public DashboardController(AnalyticsService analytics) { this.analytics = analytics; }
    @GetMapping("/summary") public Summary summary() { return analytics.summary(); }
    @GetMapping("/funnel") public Funnel funnel() { return analytics.funnel(); }
}
