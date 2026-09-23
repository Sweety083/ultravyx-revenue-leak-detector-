package com.ultravyx.leaks.service;

import com.ultravyx.leaks.api.ApiDtos.*;
import com.ultravyx.leaks.domain.Lead;
import com.ultravyx.leaks.domain.LeadStatus;
import com.ultravyx.leaks.domain.LeakType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private final LeadService leads;
    private final LeakEngine leaks;
    public AnalyticsService(LeadService leads, LeakEngine leaks) { this.leads = leads; this.leaks = leaks; }

    @Transactional(readOnly = true) public Summary summary() { return calculateSummary(leads.all(), leaks); }
    @Transactional(readOnly = true) public Funnel funnel() { return calculateFunnel(leads.all()); }
    @Transactional(readOnly = true) public List<LeakSummary> leakSummaries() { return calculateLeaks(leads.all(), leaks); }
    @Transactional(readOnly = true) public List<SourcePerformance> sources() { return calculateSources(leads.all()); }
    @Transactional(readOnly = true) public List<ResponseBucket> responseTimes() { return calculateResponseTimes(leads.all()); }

    public static Summary calculateSummary(List<Lead> all, LeakEngine engine) {
        EnumMap<LeakType, Long> counts = counts(all, engine);
        List<LeakCount> leakCounts = Arrays.stream(LeakType.values())
                .map(type -> new LeakCount(type, LeakEngine.severity(type), counts.get(type))).toList();
        long affected = all.stream().filter(l -> !engine.detect(l).isEmpty()).count();
        long flags = counts.values().stream().mapToLong(Long::longValue).sum();
        return new Summary(all.size(), all.stream().filter(l -> l.getStatus() == LeadStatus.WON).count(),
                recordedRevenue(all), affected, flags, leakCounts);
    }
    public static List<LeakSummary> calculateLeaks(List<Lead> all, LeakEngine engine) {
        EnumMap<LeakType, Long> counts = counts(all, engine);
        return Arrays.stream(LeakType.values())
                .map(type -> new LeakSummary(type, LeakEngine.severity(type), counts.get(type), description(type)))
                .toList();
    }
    private static EnumMap<LeakType, Long> counts(List<Lead> all, LeakEngine engine) {
        EnumMap<LeakType, Long> result = new EnumMap<>(LeakType.class);
        for (LeakType type : LeakType.values()) result.put(type, 0L);
        for (Lead lead : all) for (LeakFlag flag : engine.detect(lead)) result.merge(flag.type(), 1L, Long::sum);
        return result;
    }
    private static String description(LeakType type) {
        return switch (type) {
            case UNCONTACTED -> "New leads without contact for 24 hours or more";
            case UNASSIGNED -> "Active leads with no assigned owner";
            case SLOW_RESPONSE -> "First contact happened 60 minutes or more after creation";
            case QUALIFIED_NOT_PROGRESSED -> "Still qualified 7 days or more after lead creation (qualification-entry time unavailable)";
            case NO_SHOW -> "Leads whose latest status is no-show";
        };
    }
    public static Funnel calculateFunnel(List<Lead> all) {
        long total = all.size();
        long contacted = count(all, LeadStatus.CONTACTED, LeadStatus.QUALIFIED, LeadStatus.APPOINTMENT, LeadStatus.ATTENDED, LeadStatus.WON);
        long qualified = count(all, LeadStatus.QUALIFIED, LeadStatus.APPOINTMENT, LeadStatus.ATTENDED, LeadStatus.WON);
        long appointments = count(all, LeadStatus.APPOINTMENT, LeadStatus.ATTENDED, LeadStatus.WON, LeadStatus.NO_SHOW);
        long attended = count(all, LeadStatus.ATTENDED, LeadStatus.WON);
        long customers = count(all, LeadStatus.WON);
        List<FunnelStage> stages = List.of(
                new FunnelStage("Leads", total, total == 0 ? BigDecimal.ZERO : new BigDecimal("100.0")),
                new FunnelStage("Contacted", contacted, rate(contacted, total)),
                new FunnelStage("Qualified", qualified, rate(qualified, contacted)),
                new FunnelStage("Appointments", appointments, rate(appointments, qualified)),
                new FunnelStage("Attended", attended, rate(attended, appointments)),
                new FunnelStage("Customers", customers, rate(customers, attended)));
        return new Funnel(stages);
    }
    private static long count(List<Lead> all, LeadStatus... statuses) {
        EnumSet<LeadStatus> set = EnumSet.copyOf(Arrays.asList(statuses));
        return all.stream().filter(l -> set.contains(l.getStatus())).count();
    }
    public static BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }
    public static BigDecimal recordedRevenue(List<Lead> all) {
        return all.stream().map(Lead::getRevenue).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public static List<SourcePerformance> calculateSources(List<Lead> all) {
        Map<String, List<Lead>> grouped = all.stream().collect(Collectors.groupingBy(l -> LeadService.sourceName(l.getSource())));
        return grouped.entrySet().stream().map(entry -> {
            List<Lead> sourceLeads = entry.getValue();
            long won = sourceLeads.stream().filter(l -> l.getStatus() == LeadStatus.WON).count();
            return new SourcePerformance(entry.getKey(), sourceLeads.size(), won,
                    recordedRevenue(sourceLeads), rate(won, sourceLeads.size()));
        }).sorted(Comparator.comparingLong(SourcePerformance::totalLeads).reversed()
                .thenComparing(SourcePerformance::source)).toList();
    }
    public static List<ResponseBucket> calculateResponseTimes(List<Lead> all) {
        Map<String, Long> counts = new HashMap<>();
        for (Lead lead : all) {
            String bucket;
            if (lead.getContactedAt() == null || lead.getCreatedAt() == null) bucket = "No response";
            else {
                Duration duration = Duration.between(lead.getCreatedAt(), lead.getContactedAt());
                if (duration.compareTo(Duration.ofHours(1)) < 0) bucket = "< 1 hour";
                else if (duration.compareTo(Duration.ofHours(4)) < 0) bucket = "1–4 hours";
                else if (duration.compareTo(Duration.ofHours(24)) < 0) bucket = "4–24 hours";
                else bucket = ">= 24 hours";
            }
            counts.merge(bucket, 1L, Long::sum);
        }
        return List.of("< 1 hour", "1–4 hours", "4–24 hours", ">= 24 hours", "No response")
                .stream().map(bucket -> new ResponseBucket(bucket, counts.getOrDefault(bucket, 0L))).toList();
    }
}
