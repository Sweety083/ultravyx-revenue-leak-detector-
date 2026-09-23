package com.ultravyx.leaks.api;

import com.ultravyx.leaks.domain.LeadStatus;
import com.ultravyx.leaks.domain.LeakType;
import com.ultravyx.leaks.domain.Severity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}
    public record LeakFlag(LeakType type, Severity severity) {}
    public record LeadDto(UUID id, String externalLeadId, String name, LeadStatus status,
                          Instant createdAt, Instant contactedAt, String assignedTo, String source,
                          String campaign, Instant appointmentAt, Instant attendedAt,
                          BigDecimal revenue, List<LeakFlag> detectedProblems) {}
    public record CreateLeadRequest(@NotBlank @Size(max = 120) String externalLeadId,
                                    @NotBlank @Size(max = 200) String name,
                                    @NotNull LeadStatus status,
                                    @NotNull OffsetDateTime createdAt,
                                    OffsetDateTime contactedAt,
                                    @Size(max = 150) String assignedTo,
                                    @Size(max = 150) String source,
                                    @Size(max = 150) String campaign,
                                    OffsetDateTime appointmentAt,
                                    OffsetDateTime attendedAt,
                                    @DecimalMin("0.00") BigDecimal revenue) {}
    public record PagedResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
    public record RowError(long row, String field, String message) {}
    public record UploadResult(int total, int inserted, int updated, int failed, List<RowError> errors) {}
    public record LeakCount(LeakType type, Severity severity, long count) {}
    public record Summary(long totalLeads, long customers, BigDecimal recordedRevenue,
                          long affectedLeads, long totalFlags, List<LeakCount> leakCounts) {}
    public record FunnelStage(String name, long count, BigDecimal conversionRate) {}
    public record Funnel(List<FunnelStage> stages) {}
    public record LeakSummary(LeakType type, Severity severity, long count, String description) {}
    public record SourcePerformance(String source, long totalLeads, long customers,
                                    BigDecimal recordedRevenue, BigDecimal conversionRate) {}
    public record ResponseBucket(String bucket, long count) {}
    public record ApiError(Instant timestamp, int status, String code, String message, List<RowError> details) {}
}
