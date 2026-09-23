package com.ultravyx.leaks.api;

import com.ultravyx.leaks.api.ApiDtos.ResponseBucket;
import com.ultravyx.leaks.api.ApiDtos.SourcePerformance;
import com.ultravyx.leaks.service.AnalyticsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;
    public AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }
    @GetMapping("/sources") public List<SourcePerformance> sources() { return analytics.sources(); }
    @GetMapping("/response-times") public List<ResponseBucket> responseTimes() { return analytics.responseTimes(); }
}
