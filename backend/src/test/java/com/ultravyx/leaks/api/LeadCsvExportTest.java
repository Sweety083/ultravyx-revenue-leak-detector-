package com.ultravyx.leaks.api;

import com.ultravyx.leaks.api.ApiDtos.LeadDto;
import com.ultravyx.leaks.domain.LeadStatus;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeadCsvExportTest {
    @Test void preservesCsvCellsUnicodeAndUnknownRevenue() throws Exception {
        var lead = new LeadDto(UUID.randomUUID(), "A", "Asha, \"Team\"\nभारत", LeadStatus.NEW,
                Instant.parse("2026-09-23T00:00:00Z"), null, "=1+1", null, null, null, null, null, List.of());
        var zero = new LeadDto(UUID.randomUUID(), "B", "Zero", LeadStatus.WON,
                lead.createdAt(), null, null, null, null, null, null, BigDecimal.ZERO, List.of());
        var response = LeadCsvExport.response(List.of(lead, zero), "ultravyx-leads.csv");
        var output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        String csv = output.toString(StandardCharsets.UTF_8);
        assertEquals('\uFEFF', csv.charAt(0));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("ultravyx-leads.csv"));
        try (var parsed = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader(csv.substring(1)))) {
            var rows = parsed.getRecords();
            assertEquals(2, rows.size());
            assertEquals(lead.name(), rows.getFirst().get("name"));
            assertEquals("'=1+1", rows.getFirst().get("assigned_to"));
            assertEquals("", rows.getFirst().get("revenue"));
            assertEquals("0", rows.get(1).get("revenue"));
        }
    }
    @Test void emptyExportStillContainsColumnHeaders() throws Exception {
        var output = new ByteArrayOutputStream();
        LeadCsvExport.response(List.of(), "ultravyx-leads.csv").getBody().writeTo(output);
        assertEquals(1, output.toString(StandardCharsets.UTF_8).lines().count());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("detected_problems"));
    }
}
