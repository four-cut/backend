package com.fourcut.photo.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.fourcut.photo.auth.oauth.OAuthProvider;
import org.junit.jupiter.api.Test;

/**
 * 로그인할 때마다 갱신하는 프로필이 모르는 값으로 지워지지 않는지 본다.
 *
 * 애플은 이름을 최초 1회 인증에서만 준다. 두 번째 로그인부터는 null 이 오는데 그대로
 * 덮어쓰면 그 계정의 닉네임이 영영 빈다.
 */
class MemberProfileUpdateTest {

	private static Member apple() {
		return new Member(
			OAuthProvider.APPLE,
			"001234.fedcba9876543210.1234",
			"user@privaterelay.appleid.com",
			"오승연",
			null);
	}

	@Test
	void 이름을_안_주는_로그인이_기존_이름을_지우지_않는다() {
		Member member = apple();

		// 애플의 두 번째 로그인 — email 만 오고 이름은 오지 않는다.
		member.updateProfile("user@privaterelay.appleid.com", null, null);

		assertThat(member.getNickname()).isEqualTo("오승연");
	}

	@Test
	void 새_이름이_오면_갱신한다() {
		Member member = apple();

		member.updateProfile("user@privaterelay.appleid.com", "오승연2", null);

		assertThat(member.getNickname()).isEqualTo("오승연2");
	}

	@Test
	void 이메일과_사진도_같은_규칙을_따른다() {
		Member member = new Member(
			OAuthProvider.KAKAO, "12345", "before@example.com", "이름", "https://img.example/a.png");

		member.updateProfile(null, null, null);

		assertThat(member.getEmail()).isEqualTo("before@example.com");
		assertThat(member.getProfileImageUrl()).isEqualTo("https://img.example/a.png");
	}

	@Test
	void 제공자가_준_새_값은_반영한다() {
		Member member = new Member(
			OAuthProvider.KAKAO, "12345", "before@example.com", "이름", "https://img.example/a.png");

		member.updateProfile("after@example.com", "새이름", "https://img.example/b.png");

		assertThat(member.getEmail()).isEqualTo("after@example.com");
		assertThat(member.getNickname()).isEqualTo("새이름");
		assertThat(member.getProfileImageUrl()).isEqualTo("https://img.example/b.png");
	}
}
