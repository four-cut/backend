package com.fourcut.photo.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuthClientTest {

	private static final long APP_ID = 123456L;
	private static final long OTHER_APP_ID = 999999L;
	private static final String ACCESS_TOKEN = "kakao-access-token";
	private static final String BASE_URL = "https://kapi.kakao.com";
	private static final String TOKEN_INFO_URL = BASE_URL + "/v1/user/access_token_info";
	private static final String USER_INFO_URL = BASE_URL + "/v2/user/me";

	private MockRestServiceServer server;
	private KakaoOAuthClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new KakaoOAuthClient(builder.build(), properties());
	}

	private static OAuthProperties properties() {
		return new OAuthProperties(
			new OAuthProperties.Kakao(APP_ID, "admin-key"),
			new OAuthProperties.Google(List.of("client-id")),
			new OAuthProperties.Apple(List.of()));
	}

	private void expectTokenInfo(long appId) {
		server.expect(requestTo(TOKEN_INFO_URL))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
			.andRespond(withSuccess(
				"{\"id\":1,\"expires_in\":21599,\"app_id\":" + appId + "}", MediaType.APPLICATION_JSON));
	}

	@Test
	void 우리_앱에_발급된_토큰이면_프로필을_가져온다() {
		expectTokenInfo(APP_ID);
		server.expect(requestTo(USER_INFO_URL))
			.andRespond(withSuccess("""
				{
				  "id": 987654321,
				  "kakao_account": {
				    "email": "user@example.com",
				    "profile": {
				      "nickname": "홍길동",
				      "profile_image_url": "https://img.example.com/p.jpg"
				    }
				  }
				}
				""", MediaType.APPLICATION_JSON));

		OAuthUserInfo userInfo = client.fetch(ACCESS_TOKEN);

		assertThat(userInfo.provider()).isEqualTo(OAuthProvider.KAKAO);
		assertThat(userInfo.providerId()).isEqualTo("987654321");
		assertThat(userInfo.email()).isEqualTo("user@example.com");
		assertThat(userInfo.nickname()).isEqualTo("홍길동");
		assertThat(userInfo.profileImageUrl()).isEqualTo("https://img.example.com/p.jpg");
		server.verify();
	}

	// 이 테스트가 통과하지 않으면 다른 카카오 앱에서 발급된 토큰으로 남의 계정에 로그인할 수 있다.
	// 프로필 조회를 아예 호출하지 않는 것까지 확인한다. (기대하지 않은 요청이 가면 MockRestServiceServer 가 실패시킨다)
	@Test
	void 다른_앱에서_발급된_토큰은_프로필을_조회하기도_전에_거부된다() {
		expectTokenInfo(OTHER_APP_ID);

		assertThatThrownBy(() -> client.fetch(ACCESS_TOKEN))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.OAUTH_APP_MISMATCH);
		server.verify();
	}

	@Test
	void 이메일_동의를_안_한_계정은_이메일이_비어서_돌아온다() {
		expectTokenInfo(APP_ID);
		server.expect(requestTo(USER_INFO_URL))
			.andRespond(withSuccess("""
				{
				  "id": 987654321,
				  "kakao_account": {
				    "email_needs_agreement": true,
				    "profile": { "nickname": "홍길동" }
				  }
				}
				""", MediaType.APPLICATION_JSON));

		OAuthUserInfo userInfo = client.fetch(ACCESS_TOKEN);

		assertThat(userInfo.email()).isNull();
		assertThat(userInfo.profileImageUrl()).isNull();
		assertThat(userInfo.nickname()).isEqualTo("홍길동");
	}

	@Test
	void 카카오가_401을_주면_토큰_무효로_변환된다() {
		server.expect(requestTo(TOKEN_INFO_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> client.fetch(ACCESS_TOKEN))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_OAUTH_TOKEN);
	}

	@Test
	void 카카오_장애는_제공자_오류로_변환된다() {
		server.expect(requestTo(TOKEN_INFO_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> client.fetch(ACCESS_TOKEN))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.OAUTH_PROVIDER_ERROR);
	}
}
