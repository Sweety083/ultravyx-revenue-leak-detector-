package com.ultravyx.leaks.service;

import com.ultravyx.leaks.config.LeakProperties;
import com.ultravyx.leaks.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeakEngineTest {
    private final Instant now = Instant.parse("2026-09-23T12:00:00Z");
    private final LeakEngine engine = new LeakEngine(Clock.fixed(now, ZoneOffset.UTC),
            new LeakProperties(Duration.ofHours(24), Duration.ofMinutes(60), Duration.ofDays(7)));
    private final Organization org = new Organization(UUID.randomUUID(), "Test", now);

    private Lead lead(LeadStatus status, Instant created, Instant contacted, String owner) {
        Lead lead = new Lead(UUID.randomUUID(), org, UUID.randomUUID().toString());
        lead.update("Test Lead", status, created, contacted, owner, null, null, null, null,
                BigDecimal.ZERO, now);
        return lead;
    }
    private List<LeakType> flags(Lead lead) { return engine.detect(lead).stream().map(f -> f.type()).toList(); }

    @Test void uncontactedThresholdIsInclusiveAndRequiresNewUncontactedLead() {
        assertFalse(flags(lead(LeadStatus.NEW, now.minus(Duration.ofHours(24)).plusSeconds(1), null, "Owner"))
                .contains(LeakType.UNCONTACTED));
        assertTrue(flags(lead(LeadStatus.NEW, now.minus(Duration.ofHours(24)), null, "Owner"))
                .contains(LeakType.UNCONTACTED));
        assertFalse(flags(lead(LeadStatus.CONTACTED, now.minus(Duration.ofDays(2)), null, "Owner"))
                .contains(LeakType.UNCONTACTED));
    }
    @Test void slowResponseThresholdIsInclusive() {
        Instant created = now.minus(Duration.ofHours(2));
        assertFalse(flags(lead(LeadStatus.CONTACTED, created, created.plusSeconds(3599), "Owner"))
                .contains(LeakType.SLOW_RESPONSE));
        assertTrue(flags(lead(LeadStatus.CONTACTED, created, created.plusSeconds(3600), "Owner"))
                .contains(LeakType.SLOW_RESPONSE));
    }
    @Test void qualifiedAgeUsesLeadCreationTimeAtInclusiveSevenDayBoundary() {
        assertFalse(flags(lead(LeadStatus.QUALIFIED, now.minus(Duration.ofDays(7)).plusSeconds(1), null, "Owner"))
                .contains(LeakType.QUALIFIED_NOT_PROGRESSED));
        assertTrue(flags(lead(LeadStatus.QUALIFIED, now.minus(Duration.ofDays(7)), null, "Owner"))
                .contains(LeakType.QUALIFIED_NOT_PROGRESSED));
        assertFalse(flags(lead(LeadStatus.APPOINTMENT, now.minus(Duration.ofDays(8)), null, "Owner"))
                .contains(LeakType.QUALIFIED_NOT_PROGRESSED));
    }
    @Test void unassignedNoShowOverlapsAndNullFieldsAreSafe() {
        assertEquals(List.of(LeakType.UNCONTACTED, LeakType.UNASSIGNED),
                flags(lead(LeadStatus.NEW, now.minus(Duration.ofDays(2)), null, " ")));
        assertEquals(List.of(LeakType.UNASSIGNED, LeakType.NO_SHOW),
                flags(lead(LeadStatus.NO_SHOW, null, null, null)));
        assertFalse(flags(lead(LeadStatus.WON, now, null, null)).contains(LeakType.UNASSIGNED));
    }
    @Test void severitiesMatchContract() {
        assertEquals(Severity.HIGH, LeakEngine.severity(LeakType.UNCONTACTED));
        assertEquals(Severity.MEDIUM, LeakEngine.severity(LeakType.UNASSIGNED));
        assertEquals(Severity.MEDIUM, LeakEngine.severity(LeakType.SLOW_RESPONSE));
        assertEquals(Severity.HIGH, LeakEngine.severity(LeakType.QUALIFIED_NOT_PROGRESSED));
        assertEquals(Severity.HIGH, LeakEngine.severity(LeakType.NO_SHOW));
    }
}
