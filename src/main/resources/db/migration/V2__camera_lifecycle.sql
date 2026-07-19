alter table cameras add column version bigint not null default 0;
alter table cameras add column display_name_key varchar(160);
alter table cameras add column rtsp_url_key text;

create unique index uq_cameras_display_name_key on cameras (display_name_key);
create unique index uq_cameras_rtsp_url_key on cameras (rtsp_url_key);
