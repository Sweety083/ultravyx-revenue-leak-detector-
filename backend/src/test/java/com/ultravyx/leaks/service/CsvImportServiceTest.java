package com.ultravyx.leaks.service;

import com.ultravyx.leaks.api.ApiDtos.UploadResult;
import com.ultravyx.leaks.domain.Lead;
import com.ultravyx.leaks.domain.Organization;
import com.ultravyx.leaks.repo.LeadRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CsvImportServiceTest {
    private final Instant now = Instant.parse("2026-09-23T12:00:00Z");
    private LeadService service;
    private LeadRepository repo;
    private CsvImportService importer;
    private final String header = "lead_id,name,status,created_at,contacted_at,assigned_to,source,campaign,appointment_at,attended_at,revenue\n";
    @BeforeEach void setup() {
        service = mock(LeadService.class);
        repo = mock(LeadRepository.class);
        when(service.organization()).thenReturn(new Organization(UUID.randomUUID(), "Test", now));
        importer = new CsvImportService(service, repo, Clock.fixed(now, ZoneOffset.UTC));
    }
    private MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
    @Test void emptyAndMissingHeadersAreStructuralFailures() {
        assertThrows(BadRequestException.class, () -> importer.importFile(file("")));
        assertThrows(BadRequestException.class, () -> importer.importFile(file("lead_id,name\nX,Example\n")));
    }
    @Test void mixedRowsImportValidOnesAndReportInvalidEnumDateRevenueAndDuplicates() {
        String rows = "A,Alice,NEW,2026-09-22T10:00:00+05:30,,,,,,,\n"
                + "B,Bob,BAD,2026-09-22T10:00:00Z,,,,,,,\n"
                + "C,Cara,NEW,not-a-date,,,,,,,\n"
                + "D,Dave,WON,2026-09-22T10:00:00Z,,,,,,,-2\n"
                + "A,Again,NEW,2026-09-22T10:00:00Z,,,,,,,\n";
        UploadResult result = importer.importFile(file(header + rows));
        assertEquals(5, result.total());
        assertEquals(1, result.inserted());
        assertEquals(0, result.updated());
        assertEquals(4, result.failed());
        assertEquals(3, result.errors().get(0).row());
        verify(repo, times(1)).save(any(Lead.class));
    }
    @Test void existingExternalIdIsUpdatedRatherThanDuplicated() {
        Lead existing = new Lead(UUID.randomUUID(), new Organization(UUID.randomUUID(), "Test", now), "A");
        when(repo.findByOrganizationIdAndExternalLeadId(eq(LeadService.DEFAULT_ORG_ID), eq("A"))).thenReturn(Optional.of(existing));
        UploadResult result = importer.importFile(file(header + "A,Alice,WON,2026-09-22T10:00:00Z,,,,,,,125.00\n"));
        assertEquals(1, result.updated());
        assertEquals(0, result.inserted());
        assertEquals("Alice", existing.getName());
    }
    @Test void malformedQuotesAreRejected() {
        assertThrows(BadRequestException.class, () -> importer.importFile(file(header + "A,\"Alice,NEW,2026-09-22T10:00:00Z,,,,,,,\n")));
    }
}
