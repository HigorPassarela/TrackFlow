-- Invite links (module: project). Only the SHA-256 of the token is stored; the link itself is
-- shown once, to whoever created it.

create table project_invites (
    id          uuid        not null,
    row_version bigint      not null default 0,
    project_id  uuid        not null,
    token_hash  char(64)    not null,
    role        varchar(16) not null,
    created_by  uuid        not null,
    created_at  timestamptz not null,
    expires_at  timestamptz not null,
    accepted_by uuid,
    accepted_at timestamptz,
    revoked_at  timestamptz,

    constraint pk_project_invites primary key (id),
    constraint uq_project_invites_token_hash unique (token_hash),
    constraint fk_project_invites_project foreign key (project_id) references projects (id) on delete cascade,
    constraint fk_project_invites_created_by foreign key (created_by) references users (id) on delete cascade,
    constraint fk_project_invites_accepted_by foreign key (accepted_by) references users (id) on delete set null,
    -- Ownership cannot be handed out through an invite.
    constraint ck_project_invites_role check (role in ('EDITOR', 'LISTENER')),
    constraint ck_project_invites_used_once check ((accepted_by is null) = (accepted_at is null))
);

-- Pending invites of a project, newest first.
create index ix_project_invites_project_expires on project_invites (project_id, expires_at);
