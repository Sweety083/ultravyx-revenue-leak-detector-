package com.ultravyx.leaks.service;

import com.ultravyx.leaks.api.ApiDtos.RowError;
import com.ultravyx.leaks.api.ApiDtos.UploadResult;
import com.ultravyx.leaks.domain.Lead;
import com.ultravyx.leaks.domain.LeadStatus;
import com.ultravyx.leaks.repo.LeadRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvImportService {
    public static final List<String> HEADERS = List.of("lead_id", "name", "status", "created_at", "contacted_at",
            "assigned_to", "source", "campaign", "appointment_at", "attended_at", "revenue");
    private final LeadService service;
    private final LeadRepository leads;
    private final Clock clock;
    public CsvImportService(LeadService service, LeadRepository leads, Clock clock) {
        this.service = service; this.leads = leads; this.clock = clock;
    }
    @Transactional
    public UploadResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("CSV file is empty");
        String input;
        try { input = new String(file.getBytes(), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new BadRequestException("Could not read CSV file"); }
        if (input.startsWith("\uFEFF")) input = input.substring(1);
        if (input.isBlank()) throw new BadRequestException("CSV file is empty");
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setTrim(true).get();
        int inserted = 0, updated = 0, total = 0;
        List<RowError> errors = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        try (CSVParser parser = CSVParser.parse(input, format)) {
            List<String> names = parser.getHeaderNames();
            if (names.isEmpty()) throw new BadRequestException("CSV header row is missing");
            if (new HashSet<>(names).size() != names.size()) throw new BadRequestException("CSV has duplicate headers");
            List<String> missing = HEADERS.stream().filter(h -> !names.contains(h)).toList();
            if (!missing.isEmpty()) throw new BadRequestException("Missing CSV headers: " + String.join(", ", missing));
            for (CSVRecord record : parser) {
                total++;
                long row = record.getRecordNumber() + 1;
                try {
                    if (!record.isConsistent()) throw new RowProblem("row", "Column count does not match headers");
                    String externalId = required(record, "lead_id", 120);
                    if (!seenIds.add(externalId)) throw new RowProblem("lead_id", "Duplicate lead_id within this file");
                    String name = required(record, "name", 200);
                    LeadStatus status;
                    try { status = LeadStatus.valueOf(required(record, "status", 40).toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException e) { throw new RowProblem("status", "Unknown status"); }
                    Instant createdAt = timestamp(record, "created_at", true);
                    Instant contactedAt = timestamp(record, "contacted_at", false);
                    Instant appointmentAt = timestamp(record, "appointment_at", false);
                    Instant attendedAt = timestamp(record, "attended_at", false);
                    if (contactedAt != null && contactedAt.isBefore(createdAt)) throw new RowProblem("contacted_at", "Must not precede created_at");
                    BigDecimal revenue = money(record);
                    String assignedTo = optional(record, "assigned_to", 150);
                    String source = optional(record, "source", 150);
                    String campaign = optional(record, "campaign", 150);
                    Optional<Lead> existing = leads.findByOrganizationIdAndExternalLeadId(LeadService.DEFAULT_ORG_ID, externalId);
                    Lead lead = existing.orElseGet(() -> new Lead(UUID.randomUUID(), service.organization(), externalId));
                    lead.update(name, status, createdAt, contactedAt, assignedTo, source, campaign,
                            appointmentAt, attendedAt, revenue, clock.instant());
                    leads.save(lead);
                    if (existing.isPresent()) updated++; else inserted++;
                } catch (RowProblem problem) {
                    errors.add(new RowError(row, problem.field, problem.getMessage()));
                }
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            throw new BadRequestException("Malformed CSV: " + e.getMessage());
        }
        if (total == 0) throw new BadRequestException("CSV has no data rows");
        leads.flush();
        return new UploadResult(total, inserted, updated, errors.size(), errors);
    }
    private static String required(CSVRecord row, String field, int max) {
        String value = row.get(field).trim();
        if (value.isEmpty()) throw new RowProblem(field, "Required value is blank");
        if (value.length() > max) throw new RowProblem(field, "Exceeds " + max + " characters");
        return value;
    }
    private static String optional(CSVRecord row, String field, int max) {
        String value = row.get(field).trim();
        if (value.length() > max) throw new RowProblem(field, "Exceeds " + max + " characters");
        return value.isEmpty() ? null : value;
    }
    private static Instant timestamp(CSVRecord row, String field, boolean required) {
        String value = row.get(field).trim();
        if (value.isEmpty()) {
            if (required) throw new RowProblem(field, "Required value is blank");
            return null;
        }
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeParseException e) { throw new RowProblem(field, "Use ISO-8601 date/time with timezone offset"); }
    }
    private static BigDecimal money(CSVRecord row) {
        String value = row.get("revenue").trim();
        if (value.isEmpty()) return null;
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0 || parsed.scale() > 2 || parsed.precision() - parsed.scale() > 17)
                throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) { throw new RowProblem("revenue", "Use a non-negative amount with at most two decimals"); }
    }
    private static class RowProblem extends RuntimeException {
        private final String field;
        private RowProblem(String field, String message) { super(message); this.field = field; }
    }
}
