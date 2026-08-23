package com.fourcut.photo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fourcut.photo.auth.oauth.OAuthProvider;
import com.fourcut.photo.member.Member;
import com.fourcut.photo.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private String accessTokenForNewMember() {
		Member member = memberRepository.save(
			new Member(OAuthProvider.KAKAO, "987654321", "user@example.com", "홍길동", null));
		return jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
	}

	// 시큐리티 필터에서 나는 예외는 @RestControllerAdvice 를 타지 않는다.
	// 이 응답 형식이 유지되는지가 EntryPoint 위임이 동작한다는 증거다.
	@Test
	void 토큰_없이_보호된_API를_부르면_ErrorResponse_형식의_401이_온다() throws Exception {
		mockMvc.perform(get("/api/members/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
	}

	// 필터가 request 속성으로 넘긴 구체적인 코드가 응답까지 전달되는지 확인한다.
	// 클라이언트는 이 코드로 재발급과 재로그인을 구분한다.
	@Test
	void 망가진_토큰이면_일반_인증실패가_아니라_토큰_무효로_구분된다() throws Exception {
		mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	void 유효한_토큰이면_내_정보가_조회된다() throws Exception {
		mockMvc.perform(get("/api/members/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenForNewMember()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provider").value("KAKAO"))
			.andExpect(jsonPath("$.email").value("user@example.com"))
			.andExpect(jsonPath("$.nickname").value("홍길동"));
	}

	@Test
	void 기존_프레임_API는_로그인_없이_그대로_동작한다() throws Exception {
		mockMvc.perform(get("/api/frames"))
			.andExpect(status().isOk());
	}

	// 세션 API 는 익명 촬영 흐름이므로 인증으로 막히면 안 된다.
	// 프레임이 없어 404 가 나는 것은 정상이고, 401 만 아니면 된다.
	@Test
	void 기존_세션_API는_인증으로_막히지_않는다() throws Exception {
		mockMvc.perform(post("/api/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"frameId\":1}"))
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
	}

	@Test
	void 지원하지_않는_제공자는_ErrorResponse_형식의_400이_온다() throws Exception {
		mockMvc.perform(post("/api/auth/login/naver")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"whatever\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_OAUTH_PROVIDER"));
	}

	@Test
	void 애플은_아직_구현체가_없어_지원하지_않음으로_응답한다() throws Exception {
		mockMvc.perform(post("/api/auth/login/apple")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"whatever\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_OAUTH_PROVIDER"));
	}
}
