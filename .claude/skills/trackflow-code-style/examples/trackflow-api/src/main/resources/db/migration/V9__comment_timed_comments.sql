-- Timed comments (module: comment). Roots carry a position; replies point to their root.

create table timed_comments (
    id              uuid          not null,
    row_version     bigint        not null default 0,
    project_id      uuid          not null,
    version_id      uuid          not null,
    parent_id       uuid,
    author_id       uuid          not null,
    position_ms     bigint,
    end_position_ms bigint,
    body            varchar(2000) not null,
    created_at      timestamptz   not null,
    edited_at       timestamptz,
    resolved_at     timestamptz,
    resolved_by     uuid,
    -- Soft delete: a removed comment with replies keeps its thread; the API hides the body.
    deleted_at      timestamptz,

    constraint pk_timed_comments primary key (id),
    constraint fk_timed_comments_project foreign key (project_id) references projects (id) on delete cascade,
    constraint fk_timed_comments_version foreign key (version_id) references track_versions (id) on delete cascade,
    constraint fk_timed_comments_parent foreign key (parent_id) references timed_comments (id) on delete cascade,
    constraint fk_timed_comments_author foreign key (author_id) references users (id) on delete restrict,
    constraint fk_timed_comments_resolved_by foreign key (resolved_by) references users (id) on delete set null,

    -- Same invariant as the entity, enforced again where it cannot be bypassed.
    constraint ck_timed_comments_position check (
        (parent_id is null and position_ms is not null and position_ms >= 0)
        or (parent_id is not null and position_ms is null and end_position_ms is null)
    ),
    constraint ck_timed_comments_range check (end_position_ms is null or end_position_ms > position_ms)
);

-- Listing a version's comments is the hot path.
create index ix_timed_comments_version_created on timed_comments (version_id, created_at);
create index ix_timed_comments_parent on timed_comments (parent_id);
