package com.ultravyx.leaks.service;

import com.ultravyx.leaks.config.LeakProperties;
import com.ultravyx.leaks.domain.*;
import com.ultravyx.leaks.repo.LeadRepository;
import com.ultravyx.leaks.repo.OrganizationRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeadServiceTest {
    private final Instant now = Instant.parse("2026-09-23T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final Organization org = new Organization(LeadService.DEFAULT_ORG_ID, "Test", now);
    private Lead lead(String id, String revenue, Instant contactedAt) {
        Lead lead = new Lead(UUID.randomUUID(), org, id);
        lead.update(id, LeadStatus.WON, now.minusSeconds(86400), contactedAt, "Owner", null, null, null, null,
                revenue == null ? null : new BigDecimal(revenue), now);
        return lead;
    }
    private List<String> sort(String sort, Lead... records) {
        LeadRepository repo = mock(LeadRepository.class);
        when(repo.findAllByOrganizationId(LeadService.DEFAULT_ORG_ID)).thenReturn(Arrays.asList(records));
        LeakEngine engine = new LeakEngine(clock, new LeakProperties(Duration.ofHours(24), Duration.ofHours(1), Duration.ofDays(7)));
        LeadService service = new LeadService(repo, mock(OrganizationRepository.class), engine, clock);
        return service.search(null, null, null, null, 0, 20, sort).items().stream().map(l -> l.externalLeadId()).toList();
    }
    @Test void revenueSortsHighestFirstWithUnknownValuesLast() {
        assertEquals(List.of("high", "low", "zero", "unknown"), sort("revenue,desc",
                lead("unknown", null, null), lead("zero", "0", null), lead("high", "500.50", null), lead("low", "10", null)));
    }
    @Test void ascendingRevenueAlsoKeepsUnknownValuesLast() {
        assertEquals(List.of("zero", "high", "unknown"), sort("revenue,asc",
                lead("unknown", null, null), lead("high", "100", null), lead("zero", "0", null)));
    }
    @Test void latestContactSortKeepsUncontactedLeadsLast() {
        assertEquals(List.of("recent", "old", "unknown"), sort("contactedAt,desc",
                lead("unknown", null, null), lead("old", null, now.minusSeconds(3600)), lead("recent", null, now)));
    }
    @Test void equalValuesUseStableExternalIdOrderAcrossPages() {
        assertEquals(List.of("A", "B"), sort("revenue,desc", lead("B", "100", null), lead("A", "100", null)));
    }
    @Test void exportReturnsAllMatchingRowsInTheSameOrderAsSearch() {
        LeadRepository repo = mock(LeadRepository.class);
        List<Lead> records = new ArrayList<>();
        for (int i = 0; i < 25; i++) records.add(lead("MATCH-" + String.format("%02d", i), "100", null));
        records.add(lead("OTHER", "100", null));
        when(repo.findAllByOrganizationId(LeadService.DEFAULT_ORG_ID)).thenReturn(records);
        LeakEngine engine = new LeakEngine(clock, new LeakProperties(Duration.ofHours(24), Duration.ofHours(1), Duration.ofDays(7)));
        LeadService service = new LeadService(repo, mock(OrganizationRepository.class), engine, clock);
        var exported = service.export("MATCH", LeadStatus.WON, "Unknown", null, "name,desc");
        assertEquals(25, exported.size());
        assertEquals("MATCH-24", exported.getFirst().externalLeadId());
        assertEquals(service.search("MATCH", LeadStatus.WON, "Unknown", null, 0, 20, "name,desc").items(), exported.subList(0, 20));
        assertTrue(service.export("MATCH", LeadStatus.NEW, null, null, "name,asc").isEmpty());
        assertTrue(service.export("MATCH", null, "Referral", null, "name,asc").isEmpty());
        assertTrue(service.export("MATCH", null, null, LeakType.UNASSIGNED, "name,asc").isEmpty());
        assertThrows(BadRequestException.class, () -> service.export(null, null, null, null, "invalid,asc"));
    }
}
