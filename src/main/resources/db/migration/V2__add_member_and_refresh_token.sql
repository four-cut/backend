-- 소셜 로그인 도입.
-- 1) BaseTimeEntity 에 updated_at 이 추가되어 이를 상속한 기존 테이블에 컬럼을 더한다.
--    (captured_photo, frame_slot, slot_assignment 은 BaseTimeEntity 를 상속하지 않아 대상이 아니다)
-- 2) 회원과 리프레시 토큰 테이블을 만든다.

alter table frame_template  add column updated_at datetime(6);
alter table photo_session   add column updated_at datetime(6);
alter table composite_image add column updated_at datetime(6);
alter table capture_video   add column updated_at datetime(6);

-- 유니크는 (provider, provider_id) 에만 건다. 이메일로 자동 병합하면 같은 이메일을 쓰는
-- 다른 제공자 계정으로 남의 계정에 로그인할 수 있게 된다.
-- email 은 nullable 이다. 카카오 이메일은 선택 동의고 Apple 은 가림 이메일을 준다.
create table member (
    id                bigint not null auto_increment,
    provider          enum ('APPLE','GOOGLE','KAKAO') not null,
    provider_id       varchar(100) not null,
    email             varchar(255),
    nickname          varchar(255),
    profile_image_url varchar(255),
    role              enum ('ADMIN','USER') not null,
    deleted_at        datetime(6),
    created_at        datetime(6),
    updated_at        datetime(6),
    primary key (id)
) engine=InnoDB;

-- token_hash 는 토큰 원문의 SHA-256 hex(64자)다. 원문은 저장하지 않는다.
create table refresh_token (
    id         bigint not null auto_increment,
    member_id  bigint not null,
    token_hash varchar(64) not null,
    expires_at datetime(6) not null,
    revoked_at datetime(6),
    created_at datetime(6),
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB;

alter table member       add constraint uk_member_provider     unique (provider, provider_id);
alter table refresh_token add constraint uk_refresh_token_hash unique (token_hash);
alter table refresh_token add constraint fk_refresh_token_member foreign key (member_id) references member (id);
