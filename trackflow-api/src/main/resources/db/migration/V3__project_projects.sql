-- Projects (module: project). One row per track being produced.

create table projects (
    id               uuid          not null,
    row_version      bigint        not null default 0,
    title            varchar(120)  not null,
    description      varchar(2000),
    genre            varchar(60),
    owner_id         uuid          not null,
    -- Null while active; set when the owner archives the project (read-only from then on).
    archived_at      timestamptz,
    -- Bumped by edits, confirmed versions and new comments; drives the order of "my projects".
    last_activity_at timestamptz   not null,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,

    constraint pk_projects primary key (id),
    constraint fk_projects_owner foreign key (owner_id) references users (id) on delete restrict,
    constraint ck_projects_title check (char_length(title) between 1 and 120)
);

create index ix_projects_owner on projects (owner_id);
