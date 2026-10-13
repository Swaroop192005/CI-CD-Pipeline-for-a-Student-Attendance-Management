-- Passkey storage for Spring Security's WebAuthn support.
--
-- These two tables are not JPA entities, so Hibernate's ddl-auto does not
-- create them. The statements are written "if not exists" because this
-- script runs on every start against a file-mode database that survives
-- restarts, and the shape is Spring Security's own, taken from
-- user-entities-schema.sql and user-credentials-schema.sql in
-- spring-security-web. Changing a column here breaks the framework's
-- JdbcUserCredentialRepository, which expects these names exactly.

create table if not exists user_entities
(
    id           varchar(1000) not null,
    name         varchar(100)  not null,
    display_name varchar(200),
    primary key (id)
);

create table if not exists user_credentials
(
    credential_id                varchar(1000) not null,
    user_entity_user_id          varchar(1000) not null,
    public_key                   blob          not null,
    signature_count              bigint,
    uv_initialized               boolean,
    backup_eligible              boolean       not null,
    authenticator_transports     varchar(1000),
    public_key_credential_type   varchar(100),
    backup_state                 boolean       not null,
    attestation_object           blob,
    attestation_client_data_json blob,
    created                      timestamp,
    last_used                    timestamp,
    label                        varchar(1000) not null,
    primary key (credential_id)
);
