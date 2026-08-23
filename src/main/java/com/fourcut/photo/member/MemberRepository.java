package com.fourcut.photo.member;

import com.fourcut.photo.auth.oauth.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

	// 탈퇴한 회원도 함께 찾는다. (provider, provider_id) 유니크 제약 때문에
	// 재가입 시 새로 넣을 수 없고 기존 행을 되살려야 한다.
	Optional<Member> findByProviderAndProviderId(OAuthProvider provider, String providerId);

	Optional<Member> findByIdAndDeletedAtIsNull(Long id);
}
