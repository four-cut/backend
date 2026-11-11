-- 탈퇴할 때 제공자 쪽 연결을 끊는 데 쓰는 토큰.
--
-- 카카오는 관리자 키와 provider_id 만으로 끊을 수 있지만 애플은 그렇지 않다. 로그인 때 받은
-- 인가 코드를 리프레시 토큰으로 바꿔 두었다가 그 토큰으로 /auth/revoke 를 불러야 한다.
--
-- refresh_token 테이블과 달리 해시하지 않는다. 애플에 그대로 되돌려줘야 하는 값이다.
-- 이 토큰으로 할 수 있는 일은 애플 쪽 연결 해제와 애플 액세스 토큰 재발급뿐이고,
-- 우리 서비스에 로그인할 수는 없다.
alter table member
    add column provider_refresh_token varchar(255) null after profile_image_url;
