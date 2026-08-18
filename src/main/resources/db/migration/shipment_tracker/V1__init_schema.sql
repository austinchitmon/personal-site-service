-- Lookup table so the UI can present a fixed set of carrier choices and the
-- backend has one place ("code") that both the DB and the scraper
-- implementations agree on, instead of a free-text/enum column that could
-- drift ("USPS" vs "usps").
create table carriers (
    id integer generated always as identity primary key,
    code text not null unique,
    display_name text not null,
    created_at timestamptz not null default now()
);

insert into carriers (code, display_name) values ('USPS', 'USPS');

create table shipments (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references core.users(id),
    carrier_id integer not null references carriers(id),
    tracking_number text not null,
    status_text text,
    last_location text,
    estimated_delivery date,
    delivered boolean not null default false,
    delivered_at timestamptz,
    last_checked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_shipments_user_carrier_tracking unique (user_id, carrier_id, tracking_number)
);

create index idx_shipments_user_id on shipments(user_id);
