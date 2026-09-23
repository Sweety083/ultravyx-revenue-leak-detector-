package com.ultravyx.leaks.service;

import com.ultravyx.leaks.api.ApiDtos.*;
import com.ultravyx.leaks.config.LeakProperties;
import com.ultravyx.leaks.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsServiceTest {
    private final Instant now = Instant.parse("2026-09-23T12:00:00Z");
    private final Organization org = new Organization(UUID.randomUUID(), "Test", now);
    private final LeakEngine engine = new LeakEngine(Clock.fixed(now, ZoneOffset.UTC),
            new LeakProperties(Duration.ofHours(24), Duration.ofHours(1), Duration.ofDays(7)));
    private Lead lead(LeadStatus status, String source, String owner, Instant created, Instant contacted, BigDecimal revenue) {
        Lead lead = new Lead(UUID.randomUUID(), org, UUID.randomUUID().toString());
        lead.update("Test", status, created, contacted, owner, source, null, null, null, revenue, now);
        return lead;
    }
    @Test void summarySeparatesDistinctAffectedLeadsFromOverlappingFlagsAndSumsRecordedRevenueOnly() {
        List<Lead> all = List.of(
                lead(LeadStatus.NEW, null, null, now.minus(Duration.ofDays(2)), null, null),
                lead(LeadStatus.WON, "Ads", "Owner", now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(3)).plusSeconds(100), new BigDecimal("1200.50")),
                lead(LeadStatus.NO_SHOW, "Ads", null, now.minus(Duration.ofDays(3)), null, null));
        Summary result = AnalyticsService.calculateSummary(all, engine);
        assertEquals(3, result.totalLeads());
        assertEquals(1, result.customers());
        assertEquals(new BigDecimal("1200.50"), result.recordedRevenue());
        assertEquals(2, result.affectedLeads());
        assertEquals(4, result.totalFlags());
    }
    @Test void funnelUsesSpecifiedCumulativeStatusSetsAndPreviousStageRates() {
        List<Lead> all = Arrays.stream(LeadStatus.values())
                .map(status -> lead(status, "Ads", "Owner", now, null, null)).toList();
        Funnel funnel = AnalyticsService.calculateFunnel(all);
        assertEquals(List.of("Leads", "Contacted", "Qualified", "Appointments", "Attended", "Customers"),
                funnel.stages().stream().map(FunnelStage::name).toList());
        assertEquals(List.of(8L, 5L, 4L, 4L, 2L, 1L), funnel.stages().stream().map(FunnelStage::count).toList());
        assertEquals(new BigDecimal("62.5"), funnel.stages().get(1).conversionRate());
        assertEquals(new BigDecimal("80.0"), funnel.stages().get(2).conversionRate());
        assertEquals(new BigDecimal("50.0"), funnel.stages().get(4).conversionRate());
        assertEquals(BigDecimal.ZERO, AnalyticsService.calculateFunnel(List.of()).stages().get(0).conversionRate());
    }
    @Test void sourcesGroupBlankAsUnknownAndResponseBucketsRespectBoundaries() {
        Instant start = now.minus(Duration.ofDays(2));
        List<Lead> all = List.of(
                lead(LeadStatus.WON, null, "Owner", start, start.plusSeconds(3599), new BigDecimal("10.00")),
                lead(LeadStatus.NEW, " ", "Owner", start, start.plusSeconds(3600), null),
                lead(LeadStatus.WON, "Ads", "Owner", start, start.plusSeconds(14400), new BigDecimal("20.00")),
                lead(LeadStatus.CONTACTED, "Ads", "Owner", start, start.plusSeconds(86400), null),
                lead(LeadStatus.NEW, "Ads", "Owner", start, null, null));
        List<SourcePerformance> sources = AnalyticsService.calculateSources(all);
        assertEquals("Ads", sources.get(0).source());
        assertEquals("Unknown", sources.get(1).source());
        assertEquals(new BigDecimal("50.0"), sources.get(1).conversionRate());
        assertEquals(new BigDecimal("30.00"), AnalyticsService.recordedRevenue(all));
        assertEquals(List.of(1L, 1L, 1L, 1L, 1L),
                AnalyticsService.calculateResponseTimes(all).stream().map(ResponseBucket::count).toList());
    }
}
