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