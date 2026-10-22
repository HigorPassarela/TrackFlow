-- User accounts (module: identity). One row per (provider, provider account); no passwords.

create table users (
    id               uuid         not null,
    row_version      bigint       not null default 0,
    provider         varchar(16)  not null,
    provider_subject varchar(255) not null,
    -- Informational only; accounts are never linked by e-mail.
    email            varchar(320),
    display_name     varchar(60)  not null,
    avatar_url       varchar(2048),
    created_at       timestamptz  not null,

    constraint pk_users primary key (id),
    constraint uq_users_provider_subject unique (provider, provider_subject),
    constraint ck_users_provider check (provider in ('GITHUB', 'GOOGLE', 'DEV')),
    constraint ck_users_display_name check (char_length(display_name) between 2 and 60)
);
