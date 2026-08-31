-- QR 다운로드 페이지.
-- 세션 UUID 를 그대로 QR 에 담으면 촬영 API 를 남이 부를 수 있어서, 다운로드 전용 토큰을 따로 발급한다.
-- expires_at 은 세션 만료(30분)와 무관한 자체 수명이다. 부스에서 한참 뒤에 QR 을 찍어도 열려야 한다.

create table download_link (
    id         bigint not null auto_increment,
    session_id binary(16) not null,
    token      varchar(64) not null,
    expires_at datetime(6),
    created_at datetime(6),
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB;

alter table download_link add constraint UK_download_link_session unique (session_id);
alter table download_link add constraint UK_download_link_token   unique (token);
alter table download_link add constraint FK_download_link_session foreign key (session_id) references photo_session (id);
