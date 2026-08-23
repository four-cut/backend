-- 베이스라인. 이 파일은 Flyway 도입 이전에 ddl-auto: update 가 만들어 둔 스키마를 그대로 옮긴 것이다.
-- 이미 테이블이 존재하는 DB(운영)에서는 spring.flyway.baseline-on-migrate 로 인해 실행되지 않고
-- 버전 1로 표시만 되며, 빈 DB(신규 개발 환경)에서만 실제로 실행된다.
-- 엔티티 메타데이터에서 MySQLDialect 로 생성한 DDL이므로 ddl-auto: validate 와 정확히 일치한다.

create table frame_template (
    id                  bigint not null auto_increment,
    name                varchar(255),
    orientation         enum ('LANDSCAPE','PORTRAIT'),
    canvas_width        integer not null,
    canvas_height       integer not null,
    required_shot_count integer not null,
    frame_asset_key     varchar(255),
    active              bit not null,
    created_at          datetime(6),
    primary key (id)
) engine=InnoDB;

create table frame_slot (
    id                bigint not null auto_increment,
    frame_template_id bigint not null,
    slot_index        integer not null,
    x                 integer not null,
    y                 integer not null,
    width             integer not null,
    height            integer not null,
    primary key (id)
) engine=InnoDB;

create table photo_session (
    id                binary(16) not null,
    frame_template_id bigint not null,
    status            enum ('ARRANGED','CAPTURED','COMPOSED','CREATED'),
    expires_at        datetime(6),
    created_at        datetime(6),
    primary key (id)
) engine=InnoDB;

create table captured_photo (
    id         bigint not null auto_increment,
    session_id binary(16) not null,
    shot_index integer not null,
    image_key  varchar(255),
    primary key (id)
) engine=InnoDB;

create table slot_assignment (
    id                bigint not null auto_increment,
    session_id        binary(16) not null,
    frame_slot_id     bigint not null,
    captured_photo_id bigint not null,
    primary key (id)
) engine=InnoDB;

create table composite_image (
    id         bigint not null auto_increment,
    session_id binary(16) not null,
    image_key  varchar(255),
    created_at datetime(6),
    primary key (id)
) engine=InnoDB;

create table capture_video (
    id               bigint not null auto_increment,
    session_id       binary(16) not null,
    video_key        varchar(255),
    qr_code_key      varchar(255),
    duration_seconds integer,
    created_at       datetime(6),
    primary key (id)
) engine=InnoDB;

-- 유니크 제약 (@OneToOne unique = true). 이름은 Hibernate 가 생성한 것을 그대로 유지한다.
alter table capture_video   add constraint UKgt7pori3hslwv841es83jtwps unique (session_id);
alter table composite_image add constraint UKc7e3oho57txrtduho0ky8tx4w unique (session_id);

-- 외래키
alter table frame_slot      add constraint FK3cca3oicbs77qqr9t44e6xdvf foreign key (frame_template_id) references frame_template (id);
alter table photo_session   add constraint FK4yfdl6n91hh70qoe8v6n03qt3 foreign key (frame_template_id) references frame_template (id);
alter table captured_photo  add constraint FK7yt4plwfcfx2tslfftofvslt9 foreign key (session_id) references photo_session (id);
alter table composite_image add constraint FKkdm37hwnayybtwmmtvswvlfn7 foreign key (session_id) references photo_session (id);
alter table capture_video   add constraint FK1akc7vn2ru95jrj92lemmm11w foreign key (session_id) references photo_session (id);
alter table slot_assignment add constraint FKk72peylmnaxs94xeumxv1sj1  foreign key (session_id) references photo_session (id);
alter table slot_assignment add constraint FKrncu0pb9yv03kqibk3rw256ng foreign key (frame_slot_id) references frame_slot (id);
alter table slot_assignment add constraint FKd21sjub52169h3nr6imi1k8se foreign key (captured_photo_id) references captured_photo (id);
