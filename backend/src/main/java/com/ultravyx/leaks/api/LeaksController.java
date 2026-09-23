package com.ultravyx.leaks.api;

import com.ultravyx.leaks.api.ApiDtos.LeadDto;
import com.ultravyx.leaks.api.ApiDtos.LeakSummary;
import com.ultravyx.leaks.api.ApiDtos.PagedResponse;
import com.ultravyx.leaks.domain.LeakType;
import com.ultravyx.leaks.service.AnalyticsService;
import com.ultravyx.leaks.service.LeadService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController @RequestMapping("/api/leaks")
public class LeaksController {
    private final AnalyticsService analytics;
    private final LeadService leads;
    public LeaksController(AnalyticsService analytics, LeadService leads) { this.analytics = analytics; this.leads = leads; }
    @GetMapping public List<LeakSummary> list() { return analytics.leakSummaries(); }
    @GetMapping("/{type}/leads") public PagedResponse<LeadDto> byType(
            @PathVariable LeakType type, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return leads.byLeak(type, page, size, sort);
    }
    @GetMapping(value = "/{type}/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> export(@PathVariable LeakType type) {
        List<LeadDto> affected = leads.exportByLeak(type);
        return LeadCsvExport.response(affected, type.name().toLowerCase(java.util.Locale.ROOT) + "-leads.csv");
    }
}
