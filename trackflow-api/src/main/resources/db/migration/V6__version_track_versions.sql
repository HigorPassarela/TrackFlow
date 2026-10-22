-- Track versions (module: version). A row exists from the upload request on; it only gets a
-- number when the upload is confirmed.

create table track_versions (
    id                 uuid          not null,
    row_version        bigint        not null default 0,
    project_id         uuid          not null,
    number             integer,
    status             varchar(16)   not null,
    title              varchar(80),
    notes              varchar(2000),
    uploaded_by        uuid          not null,
    original_file_name varchar(255)  not null,
    content_type       varchar(100)  not null,
    size_bytes         bigint        not null,
    source_key         varchar(512)  not null,
    -- Optional Ableton Live project attached to the same upload.
    als_key            varchar(512),
    als_file_name      varchar(255),
    als_size_bytes     bigint,
    -- Filled by the worker when processing succeeds.
    preview_key        varchar(512),
    peaks_key          varchar(512),
    duration_ms        bigint,
    bpm_estimated      numeric(5, 1),
    bpm_override       numeric(5, 1),
    loudness_lufs      numeric(5, 1),
    true_peak_dbtp     numeric(5, 1),
    failure_reason     varchar(64),
    -- The job in charge of the current attempt; results from other jobs are ignored (D8).
    processing_job_id  uuid,
    created_at         timestamptz   not null,
    confirmed_at       timestamptz,
    updated_at         timestamptz   not null,

    constraint pk_track_versions primary key (id),
    constraint uq_track_versions_project_number unique (project_id, number),
    constraint fk_track_versions_project foreign key (project_id) references projects (id) on delete cascade,
    constraint fk_track_versions_uploaded_by foreign key (uploaded_by) references users (id) on delete restrict,
    constraint ck_track_versions_status check (status in ('PENDING_UPLOAD', 'UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    constraint ck_track_versions_numbered check ((status = 'PENDING_UPLOAD') = (number is null)),
    constraint ck_track_versions_size check (size_bytes > 0),
    constraint ck_track_versions_bpm_override check (bpm_override is null or bpm_override between 40 and 250)
);

-- History of a project, newest first.
create index ix_track_versions_project_number on track_versions (project_id, number desc);
-- Cleanup of abandoned uploads.
create index ix_track_versions_pending_created on track_versions (created_at) where status = 'PENDING_UPLOAD';
