-- Minimal rows the comment tests need: two users, one project with both as members, one ready
-- version, and a second project to prove queries never leak across projects.

insert into users (id, provider, provider_subject, email, display_name, created_at)
values ('00000000-0000-0000-0000-00000000000a', 'GITHUB', '1001', 'verxt@example.com', 'VERXT', now()),
       ('00000000-0000-0000-0000-00000000000b', 'GITHUB', '1002', 'parceira@example.com', 'Parceira', now());

insert into projects (id, title, owner_id, last_activity_at, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000001', 'Ibiza Vibes', '00000000-0000-0000-0000-00000000000a', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000099', 'Outro projeto', '00000000-0000-0000-0000-00000000000b', now(), now(), now());

insert into project_members (project_id, user_id, role, joined_at)
values ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000000a', 'OWNER', now()),
       ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000000b', 'LISTENER', now()),
       ('00000000-0000-0000-0000-000000000099', '00000000-0000-0000-0000-00000000000b', 'OWNER', now());

insert into track_versions (id, project_id, number, status, uploaded_by, original_file_name,
                            content_type, size_bytes, source_key, duration_ms, created_at,
                            confirmed_at, updated_at)
values ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 4, 'READY',
        '00000000-0000-0000-0000-00000000000a', 'ibiza-vibes-v4.wav', 'audio/wav', 63504000,
        'projects/00000000-0000-0000-0000-000000000001/versions/00000000-0000-0000-0000-000000000002/source.wav',
        360000, now(), now(), now());
