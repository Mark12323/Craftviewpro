create table cameras (
    id uuid primary key,
    display_name varchar(160) not null,
    rtsp_url text not null,
    onvif_service_url text,
    manufacturer varchar(160),
    model varchar(160),
    host varchar(255),
    location varchar(255),
    building varchar(160),
    floor varchar(80),
    zone varchar(160),
    priority varchar(32) not null,
    status varchar(32) not null,
    enabled boolean not null,
    credential_reference varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_cameras_enabled on cameras (enabled);
create index idx_cameras_priority on cameras (priority);
create index idx_cameras_status on cameras (status);
create index idx_cameras_zone on cameras (zone);
