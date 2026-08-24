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

	// nickname 만 null 이어도 기존 값을 유지한다. 애플은 이름을 최초 인가 시 딱 한 번만 주고
	// 그 뒤로는 다시 주지 않으므로, 여기서 무조건 덮어쓰면 두 번째 로그인부터 이름이 지워진다.
	// email/profileImageUrl 은 매 로그인마다 제공자가 다시 내려주는 값이라 그대로 동기화한다.
	public void updateProfile(String email, String nickname, String profileImageUrl) {
		this.email = email;
		if (nickname != null) {
			this.nickname = nickname;
		}
		this.profileImageUrl = profileImageUrl;
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
