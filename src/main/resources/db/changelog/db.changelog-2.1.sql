--liquibase formatted sql

--changeset rollie:1
ALTER TABLE users
    ADD column audit_created_by VARCHAR(255);

ALTER TABLE tickets
    ADD column audit_created_by VARCHAR(255);

ALTER TABLE tickets
    ADD column modified_by VARCHAR(255);

ALTER TABLE ticket_comments
    ADD column audit_created_by VARCHAR(255);

ALTER TABLE ticket_events
    ADD COLUMN audit_created_by VARCHAR(255);