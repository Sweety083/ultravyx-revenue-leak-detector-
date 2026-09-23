package com.ultravyx.leaks.service;

import com.ultravyx.leaks.api.ApiDtos.LeakFlag;
import com.ultravyx.leaks.config.LeakProperties;
import com.ultravyx.leaks.domain.Lead;
import com.ultravyx.leaks.domain.LeadStatus;
import com.ultravyx.leaks.domain.LeakType;
import com.ultravyx.leaks.domain.Severity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LeakEngine {
    private final Clock clock;
    private final LeakProperties thresholds;
    public LeakEngine(Clock clock, LeakProperties thresholds) { this.clock = clock; this.thresholds = thresholds; }

    public List<LeakFlag> detect(Lead lead) {
        Instant now = clock.instant();
        List<LeakFlag> flags = new ArrayList<>();
        if (lead.getStatus() == LeadStatus.NEW && lead.getContactedAt() == null
                && elapsedAtLeast(lead.getCreatedAt(), now, thresholds.uncontacted())) {
            flags.add(flag(LeakType.UNCONTACTED));
        }
        if (lead.getStatus() != LeadStatus.WON && lead.getStatus() != LeadStatus.LOST
                && (lead.getAssignedTo() == null || lead.getAssignedTo().isBlank())) {
            flags.add(flag(LeakType.UNASSIGNED));
        }
        if (lead.getCreatedAt() != null && lead.getContactedAt() != null
                && elapsedAtLeast(lead.getCreatedAt(), lead.getContactedAt(), thresholds.slowResponse())) {
            flags.add(flag(LeakType.SLOW_RESPONSE));
        }
        // Qualification-entry time is unavailable: this is intentionally based on lead creation.
        if (lead.getStatus() == LeadStatus.QUALIFIED
                && elapsedAtLeast(lead.getCreatedAt(), now, thresholds.qualifiedNotProgressed())) {
            flags.add(flag(LeakType.QUALIFIED_NOT_PROGRESSED));
        }
        if (lead.getStatus() == LeadStatus.NO_SHOW) flags.add(flag(LeakType.NO_SHOW));
        return List.copyOf(flags);
    }

    private static boolean elapsedAtLeast(Instant start, Instant end, Duration threshold) {
        return start != null && end != null && !end.isBefore(start.plus(threshold));
    }
    public static LeakFlag flag(LeakType type) { return new LeakFlag(type, severity(type)); }
    public static Severity severity(LeakType type) {
        return switch (type) {
            case UNCONTACTED, QUALIFIED_NOT_PROGRESSED, NO_SHOW -> Severity.HIGH;
            case UNASSIGNED, SLOW_RESPONSE -> Severity.MEDIUM;
        };
    }
}
