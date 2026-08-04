-- Mirrors Supabase auth.users (id = the JWT `sub` claim) so other schemas in
-- this database can hold real foreign keys to a user, and so app-specific
-- profile data (e.g. settings) has somewhere to live. Populated via
-- just-in-time upsert on each authenticated request, not owned/generated here.
create table users (
    id uuid primary key,
    email text not null,
    display_name text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
