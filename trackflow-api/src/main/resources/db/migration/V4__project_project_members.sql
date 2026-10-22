-- Project members and their roles (module: project).

create table project_members (
    project_id uuid        not null,
    user_id    uuid        not null,
    role       varchar(16) not null,
    joined_at  timestamptz not null,

    constraint pk_project_members primary key (project_id, user_id),
    constraint fk_project_members_project foreign key (project_id) references projects (id) on delete cascade,
    constraint fk_project_members_user foreign key (user_id) references users (id) on delete cascade,
    constraint ck_project_members_role check (role in ('OWNER', 'EDITOR', 'LISTENER'))
);

-- The list of my projects starts from the memberships of the user.
create index ix_project_members_user on project_members (user_id);

-- Exactly one owner per project, enforced where it cannot be bypassed.
create unique index uq_project_members_single_owner on project_members (project_id) where role = 'OWNER';
