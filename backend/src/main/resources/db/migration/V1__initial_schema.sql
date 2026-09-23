CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO organizations (id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'ULTRAVYX Demo', CURRENT_TIMESTAMP);

CREATE TABLE leads (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    external_lead_id VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(40) NOT NULL,
    lead_created_at TIMESTAMPTZ NOT NULL,
    contacted_at TIMESTAMPTZ,
    assigned_to VARCHAR(150),
    source VARCHAR(150),
    campaign VARCHAR(150),
    appointment_at TIMESTAMPTZ,
    attended_at TIMESTAMPTZ,
    revenue NUMERIC(19,2),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_lead_external UNIQUE (organization_id, external_lead_id),
    CONSTRAINT ck_lead_revenue_nonnegative CHECK (revenue IS NULL OR revenue >= 0),
    CONSTRAINT ck_lead_status CHECK (status IN ('NEW','CONTACTED','QUALIFIED','APPOINTMENT','ATTENDED','WON','LOST','NO_SHOW'))
);
CREATE INDEX ix_leads_organization_created ON leads (organization_id, lead_created_at DESC);
CREATE INDEX ix_leads_organization_status ON leads (organization_id, status);
CREATE INDEX ix_leads_organization_source ON leads (organization_id, source);
