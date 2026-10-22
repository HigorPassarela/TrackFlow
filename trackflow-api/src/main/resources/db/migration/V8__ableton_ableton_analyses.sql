-- Ableton project analyses (module: ableton). Lifecycle independent of the version status (D10).

create table ableton_analyses (
    version_id       uuid         not null,
    row_version      bigint       not null default 0,
    project_id       uuid         not null,
    status           varchar(16)  not null,
    failure_reason   varchar(64),
    -- The job in charge of the analysis; results from other jobs are ignored.
    job_id           uuid         not null,
    live_version     varchar(100),
    verified_version boolean      not null default false,
    tempo            numeric(6, 2),
    time_signature   varchar(10),
    -- [{id, type, name, devices[], plugins[]}]; read and written as a whole, never queried inside.
    tracks           jsonb        not null default '[]',
    created_at       timestamptz  not null,
    analyzed_at      timestamptz,

    constraint pk_ableton_analyses primary key (version_id),
    constraint fk_ableton_analyses_version foreign key (version_id) references track_versions (id) on delete cascade,
    constraint fk_ableton_analyses_project foreign key (project_id) references projects (id) on delete cascade,
    constraint ck_ableton_analyses_status check (status in ('PENDING', 'DONE', 'FAILED'))
);
