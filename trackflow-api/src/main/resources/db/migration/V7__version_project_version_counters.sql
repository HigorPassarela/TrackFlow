-- Version numbering per project (module: version, design D5). The row lock taken by the atomic
-- upsert serializes concurrent confirmations of the same project; projects do not block each other.

create table project_version_counters (
    project_id  uuid    not null,
    last_number integer not null,

    constraint pk_project_version_counters primary key (project_id),
    constraint fk_project_version_counters_project foreign key (project_id) references projects (id) on delete cascade,
    constraint ck_project_version_counters_positive check (last_number > 0)
);
