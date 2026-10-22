-- Public listening links (module: version). Only the SHA-256 of the token is stored.

create table share_links (
    id          uuid        not null,
    row_version bigint      not null default 0,
    project_id  uuid        not null,
    version_id  uuid        not null,
    token_hash  char(64)    not null,
    created_by  uuid        not null,
    created_at  timestamptz not null,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,

    constraint pk_share_links primary key (id),
    constraint uq_share_links_token_hash unique (token_hash),
    constraint fk_share_links_project foreign key (project_id) references projects (id) on delete cascade,
    constraint fk_share_links_version foreign key (version_id) references track_versions (id) on delete cascade,
    constraint fk_share_links_created_by foreign key (created_by) references users (id) on delete cascade,
    constraint ck_share_links_validity check (expires_at > created_at)
);

create index ix_share_links_version on share_links (version_id);
