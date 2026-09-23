package com.ultravyx.leaks.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "leads")
public class Lead {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    @Column(name = "external_lead_id", nullable = false, length = 120) private String externalLeadId;
    @Column(nullable = false, length = 200) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private LeadStatus status;
    @Column(name = "lead_created_at", nullable = false) private Instant createdAt;
    @Column(name = "contacted_at") private Instant contactedAt;
    @Column(name = "assigned_to", length = 150) private String assignedTo;
    @Column(length = 150) private String source;
    @Column(length = 150) private String campaign;
    @Column(name = "appointment_at") private Instant appointmentAt;
    @Column(name = "attended_at") private Instant attendedAt;
    @Column(precision = 19, scale = 2) private BigDecimal revenue;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Lead() {}
    public Lead(UUID id, Organization organization, String externalLeadId) { this.id = id; this.organization = organization; this.externalLeadId = externalLeadId; }
    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public String getExternalLeadId() { return externalLeadId; }
    public String getName() { return name; }
    public LeadStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getContactedAt() { return contactedAt; }
    public String getAssignedTo() { return assignedTo; }
    public String getSource() { return source; }
    public String getCampaign() { return campaign; }
    public Instant getAppointmentAt() { return appointmentAt; }
    public Instant getAttendedAt() { return attendedAt; }
    public BigDecimal getRevenue() { return revenue; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(String name, LeadStatus status, Instant createdAt, Instant contactedAt, String assignedTo, String source, String campaign, Instant appointmentAt, Instant attendedAt, BigDecimal revenue, Instant updatedAt) {
        this.name = name; this.status = status; this.createdAt = createdAt; this.contactedAt = contactedAt;
        this.assignedTo = assignedTo; this.source = source; this.campaign = campaign; this.appointmentAt = appointmentAt;
        this.attendedAt = attendedAt; this.revenue = revenue; this.updatedAt = updatedAt;
    }
}
