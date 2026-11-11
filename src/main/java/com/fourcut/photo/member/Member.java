package com.fourcut.photo.member;

import com.fourcut.photo.auth.oauth.OAuthProvider;
import com.fourcut.photo.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 유니크 제약은 (provider, provider_id) 에만 건다. 이메일로 자동 병합하면
// 같은 이메일을 쓰는 다른 제공자 계정으로 남의 계정에 로그인할 수 있게 된다.
@Getter
@Entity
@Table(
	name = "member",
	uniqueConstraints = @UniqueConstraint(name = "uk_member_provider", columnNames = {"provider", "provider_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OAuthProvider provider;

	@Column(name = "provider_id", nullable = false, length = 100)
	private String providerId;

	// 카카오 이메일은 선택 동의 항목이고 Apple 은 가림 이메일을 주므로 없을 수 있다.
	private String email;

	private String nickname;

	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MemberRole role;

	private LocalDateTime deletedAt;

	public Member(OAuthProvider provider, String providerId, String email, String nickname, String profileImageUrl) {
		this.provider = provider;
		this.providerId = providerId;
		this.email = email;
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.role = MemberRole.USER;
	}

	/**
	 * 로그인할 때마다 제공자가 준 프로필로 갱신한다.
	 *
	 * null 은 "제공자가 이번에 알려 주지 않았다"는 뜻이라 기존 값을 둔다. 덮어쓰면 애플
	 * 계정의 이름이 두 번째 로그인에서 지워진다 — 애플은 이름을 최초 1회만 주기 때문이다.
	 * 대신 사용자가 카카오·구글에서 프로필 사진을 지워도 우리 쪽에는 남는다. 이름이 사라지는
	 * 쪽이 더 나쁘다고 보고 이렇게 뒀다.
	 */
	public void updateProfile(String email, String nickname, String profileImageUrl) {
		if (email != null) {
			this.email = email;
		}
		if (nickname != null) {
			this.nickname = nickname;
		}
		if (profileImageUrl != null) {
			this.profileImageUrl = profileImageUrl;
		}
	}

	public void withdraw() {
		this.deletedAt = LocalDateTime.now();
	}

	public void restore() {
		this.deletedAt = null;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}
}
