--liquibase formatted sql

--changeset rollie:1
CREATE TABLE users
(
    id         SERIAL PRIMARY KEY,
    full_name  VARCHAR(128)        NOT NULL,
    email      VARCHAR(255) UNIQUE NOT NULL,
    role       VARCHAR(16)         NOT NULL,
    created_at TIMESTAMPTZ         NOT NULL
);

--changeset rollie:2
CREATE TABLE tickets
(
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(128)              NOT NULL,
    description    TEXT                      NOT NULL,
    status         VARCHAR(32)               NOT NULL,
    priority       VARCHAR(16)               NOT NULL,
    created_by_id  INT REFERENCES users (id) NOT NULL,
    assigned_to_id INT REFERENCES users (id),
    created_at     TIMESTAMPTZ               NOT NULL,
    modified_at    TIMESTAMPTZ,
    resolved_at    TIMESTAMPTZ,
    version        INT                       NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets (status);
CREATE INDEX IF NOT EXISTS idx_tickets_created_by_id ON tickets (created_by_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned_to_id ON tickets (assigned_to_id);
-- CREATE INDEX IF NOT EXISTS idx_tickets_created_at ON tickets (created_at);

--changeset rollie:3
CREATE TABLE ticket_comments
(
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT REFERENCES tickets (id) NOT NULL,
    author_id  INT REFERENCES users (id)      NOT NULL,
    body       TEXT                           NOT NULL,
    created_at TIMESTAMPTZ                    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ticket_comments_ticket_id ON ticket_comments (ticket_id);

--changeset rollie:4
CREATE TABLE ticket_events
(
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT REFERENCES tickets (id) NOT NULL,
    actor_id   INT REFERENCES users (id)      NOT NULL,
    event_type VARCHAR(32)                    NOT NULL,
    payload    JSONB                          NOT NULL,
    created_at TIMESTAMPTZ                    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ticket_events_ticket_id ON ticket_events (ticket_id);