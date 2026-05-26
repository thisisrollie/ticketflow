--liquibase formatted sql

--changeset rollie:1
CREATE TABLE sla_policies
(
    id                         SERIAL PRIMARY KEY,
    priority                   VARCHAR(16) UNIQUE NOT NULL,
    first_response_due_minutes INT                NOT NULL,
    resolution_due_minutes     INT                NOT NULL,

    CONSTRAINT chk_sla_policy_first_response_positive
        CHECK (first_response_due_minutes > 0),

    CONSTRAINT chk_sla_policy_resolution_positive
        CHECK (resolution_due_minutes > 0),

    CONSTRAINT chk_sla_policy_resolution_gt_first_response
        CHECK (resolution_due_minutes > first_response_due_minutes)
);

INSERT INTO sla_policies (priority, first_response_due_minutes, resolution_due_minutes)
VALUES ('LOW', 2880, 10080),
       ('MEDIUM', 1440, 4320),
       ('HIGH', 480, 1440),
       ('CRITICAL', 120, 480);


--changeset rollie:2
ALTER TABLE tickets
    ADD COLUMN first_responded_at       TIMESTAMPTZ,
    ADD COLUMN first_response_deadline  TIMESTAMPTZ,
    ADD COLUMN resolution_deadline      TIMESTAMPTZ,
    ADD COLUMN response_sla_status      VARCHAR(16),
    ADD COLUMN resolution_sla_status    VARCHAR(16),
    ADD COLUMN resolution_sla_paused_at TIMESTAMPTZ;


--changeset rollie:3
ALTER TABLE ticket_events
    ALTER COLUMN actor_id DROP NOT NULL;
