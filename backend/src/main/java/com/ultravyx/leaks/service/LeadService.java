package com.ultravyx.leaks.service;

import com.ultravyx.leaks.api.ApiDtos.*;
import com.ultravyx.leaks.domain.*;
import com.ultravyx.leaks.repo.LeadRepository;
import com.ultravyx.leaks.repo.OrganizationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {
    public static final UUID DEFAULT_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final LeadRepository leads;
    private final OrganizationRepository organizations;
    private final LeakEngine leaks;
    private final Clock clock;
    public LeadService(LeadRepository leads, OrganizationRepository organizations, LeakEngine leaks, Clock clock) {
        this.leads = leads; this.organizations = organizations; this.leaks = leaks; this.clock = clock;
    }
    public Organization organization() {
        return organizations.findById(DEFAULT_ORG_ID).orElseThrow(() -> new IllegalStateException("Default organization missing; run migrations"));
    }
    @Transactional(readOnly = true)
    public List<Lead> all() { return leads.findAllByOrganizationId(DEFAULT_ORG_ID); }
    public LeadDto dto(Lead lead) {
        return new LeadDto(lead.getId(), lead.getExternalLeadId(), lead.getName(), lead.getStatus(),
                lead.getCreatedAt(), lead.getContactedAt(), lead.getAssignedTo(), lead.getSource(), lead.getCampaign(),
                lead.getAppointmentAt(), lead.getAttendedAt(), lead.getRevenue(), leaks.detect(lead));
    }
    @Transactional(readOnly = true)
    public LeadDto find(UUID id) {
        return dto(leads.findByIdAndOrganizationId(id, DEFAULT_ORG_ID).orElseThrow(() -> new NotFoundException("Lead not found")));
    }
    @Transactional
    public LeadDto create(CreateLeadRequest request) {
        if (request.contactedAt() != null && request.contactedAt().isBefore(request.createdAt()))
            throw new BadRequestException("contactedAt must not precede createdAt");
        if (request.revenue() != null && (request.revenue().scale() > 2
                || request.revenue().precision() - request.revenue().scale() > 17))
            throw new BadRequestException("revenue must have at most two decimals and fit NUMERIC(19,2)");
        String externalId = request.externalLeadId().trim();
        if (leads.findByOrganizationIdAndExternalLeadId(DEFAULT_ORG_ID, externalId).isPresent()) {
            throw new ConflictException("External lead ID already exists");
        }
        Lead lead = new Lead(UUID.randomUUID(), organization(), externalId);
        lead.update(request.name().trim(), request.status(), toInstant(request.createdAt()),
                toInstant(request.contactedAt()), blankToNull(request.assignedTo()), blankToNull(request.source()),
                blankToNull(request.campaign()), toInstant(request.appointmentAt()), toInstant(request.attendedAt()),
                request.revenue(), clock.instant());
        return dto(leads.saveAndFlush(lead));
    }
    @Transactional(readOnly = true)
    public PagedResponse<LeadDto> search(String search, LeadStatus status, String source, LeakType leakType,
                                         int page, int size, String sort) {
        List<LeadDto> filtered = filterAndSort(search, status, source, leakType, sort);
        return page(filtered, page, size);
    }
    @Transactional(readOnly = true)
    public PagedResponse<LeadDto> byLeak(LeakType type, int page, int size, String sort) {
        return search(null, null, null, type, page, size, sort);
    }
    @Transactional(readOnly = true)
    public List<LeadDto> exportByLeak(LeakType type) { return filterAndSort(null, null, null, type, "createdAt,desc"); }

    @Transactional(readOnly = true)
    public List<LeadDto> export(String search, LeadStatus status, String source, LeakType leakType, String sort) {
        return filterAndSort(search, status, source, leakType, sort);
    }

    private List<LeadDto> filterAndSort(String search, LeadStatus status, String source, LeakType leakType, String sort) {
        Comparator<LeadDto> comparator = comparator(sort);
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String sourceTerm = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        Stream<LeadDto> stream = leads.findAllByOrganizationId(DEFAULT_ORG_ID).stream().map(this::dto);
        return stream.filter(l -> term.isEmpty() || contains(l.name(), term) || contains(l.externalLeadId(), term)
                        || contains(l.source(), term) || contains(l.campaign(), term))
                .filter(l -> status == null || l.status() == status)
                .filter(l -> sourceTerm.isEmpty() || sourceName(l.source()).toLowerCase(Locale.ROOT).equals(sourceTerm))
                .filter(l -> leakType == null || l.detectedProblems().stream().anyMatch(f -> f.type() == leakType))
                .sorted(comparator).toList();
    }
    private static boolean contains(String value, String term) { return value != null && value.toLowerCase(Locale.ROOT).contains(term); }
    public static String sourceName(String source) { return source == null || source.isBlank() ? "Unknown" : source.trim(); }
    public static <T> PagedResponse<T> page(List<T> data, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("page must be >= 0 and size must be 1–100");
        long start = (long) page * size;
        int totalPages = (int) ((data.size() + (long) size - 1) / size);
        if (start >= data.size()) return new PagedResponse<>(List.of(), page, size, data.size(), totalPages);
        int from = (int) start;
        return new PagedResponse<>(data.subList(from, (int) Math.min(start + size, data.size())), page, size, data.size(), totalPages);
    }
    private static Comparator<LeadDto> comparator(String sort) {
        String[] parts = (sort == null || sort.isBlank() ? "createdAt,desc" : sort).split(",", -1);
        if (parts.length != 2 || (!parts[1].equalsIgnoreCase("asc") && !parts[1].equalsIgnoreCase("desc")))
            throw new BadRequestException("sort must be an allowed field followed by asc or desc");
        boolean descending = parts[1].equalsIgnoreCase("desc");
        Comparator<Instant> timeOrder = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        Comparator<java.math.BigDecimal> revenueOrder = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        Comparator<String> textOrder = descending ? String.CASE_INSENSITIVE_ORDER.reversed() : String.CASE_INSENSITIVE_ORDER;
        Comparator<LeadDto> base = switch (parts[0]) {
            case "createdAt" -> Comparator.comparing(LeadDto::createdAt, Comparator.nullsLast(timeOrder));
            case "name" -> Comparator.comparing(LeadDto::name, Comparator.nullsLast(textOrder));
            case "status" -> Comparator.comparing(l -> l.status().name(), textOrder);
            case "source" -> Comparator.comparing(l -> sourceName(l.source()), textOrder);
            case "revenue" -> Comparator.comparing(LeadDto::revenue, Comparator.nullsLast(revenueOrder));
            case "contactedAt" -> Comparator.comparing(LeadDto::contactedAt, Comparator.nullsLast(timeOrder));
            default -> throw new BadRequestException("Unsupported sort field: " + parts[0]);
        };
        return base.thenComparing(LeadDto::externalLeadId);
    }
    public static Instant toInstant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
    public static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
