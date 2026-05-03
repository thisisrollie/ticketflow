--liquibase formatted sql

--changeset rollie:1
ALTER TABLE users
    ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT '{noop}123'