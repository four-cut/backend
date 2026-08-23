package com.fourcut.photo.auth.oauth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoOAuthClient implements OAuthClient {

	private static final Logger log = LoggerFactory.getLogger(KakaoOAuthClient.class);

	private static final String TOKEN_INFO_URI = "/v1/user/access_token_info";
	private static final String USER_INFO_URI = "/v2/user/me";
	private static final String UNLINK_URI = "/v1/user/unlink";

	// 응답을 Map 으로 받는다. 카카오는 snake_case 인데 전역 네이밍 전략을 바꾸면 기존 DTO 가 전부
	// 영향을 받고, Jackson 어노테이션은 버전에 따라 패키지가 갈린다. 엔드포인트가 셋뿐이라
	// 경로를 직접 읽는 편이 결합도 없이 명확하다.
	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient kakaoRestClient;
	private final Long appId;
	private final String adminKey;

	public KakaoOAuthClient(RestClient kakaoRestClient, OAuthProperties properties) {
		this.kakaoRestClient = kakaoRestClient;
		this.appId = properties.kakao().appId();
		this.adminKey = properties.kakao().adminKey();
	}

	@Override
	public OAuthProvider provider() {
		return OAuthProvider.KAKAO;
	}

	@Override
	public OAuthUserInfo fetch(String accessToken) {
		verifyIssuedToThisApp(accessToken);

		Map<String, Object> user = get(USER_INFO_URI, accessToken);
		Map<String, Object> account = nested(user, "kakao_account");
		Map<String, Object> profile = nested(account, "profile");

		return new OAuthUserInfo(
			OAuthProvider.KAKAO,
			String.valueOf(user.get("id")),
			text(account, "email"),
			text(profile, "nickname"),
			text(profile, "profile_image_url"));
	}

	// 액세스 토큰 자체에는 어느 앱에 발급됐는지가 들어있지 않다. 이 검사를 빼면 공격자가
	// 자기 카카오 앱에서 받은 피해자의 토큰을 그대로 우리 서버에 넘겨 로그인할 수 있다.
	// 값싼 실패 경로이므로 프로필 조회보다 먼저 호출한다.
	private void verifyIssuedToThisApp(String accessToken) {
		Map<String, Object> tokenInfo = get(TOKEN_INFO_URI, accessToken);
		Long tokenAppId = toLong(tokenInfo.get("app_id"));
		if (appId == null || !appId.equals(tokenAppId)) {
			log.warn("카카오 앱 ID 불일치. 기대={} 실제={}", appId, tokenAppId);
			throw new ApiException(ErrorCode.OAUTH_APP_MISMATCH);
		}
	}

	@Override
	public void unlink(String providerId) {
		if (adminKey == null || adminKey.isBlank()) {
			log.warn("카카오 관리자 키가 설정되지 않아 연결 해제를 건너뜁니다. providerId={}", providerId);
			return;
		}
		try {
			kakaoRestClient.post()
				.uri(UNLINK_URI)
				.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body("target_id_type=user_id&target_id=" + providerId)
				.retrieve()
				.toBodilessEntity();
		} catch (RestClientException e) {
			// 연결 해제 실패가 탈퇴 자체를 막으면 안 된다. 수동 조치를 위해 식별자를 남기고 진행한다.
			log.error("카카오 연결 해제 실패. providerId={}", providerId, e);
		}
	}

	private Map<String, Object> get(String uri, String accessToken) {
		Map<String, Object> body;
		try {
			body = kakaoRestClient.get()
				.uri(uri)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
					throw new ApiException(ErrorCode.INVALID_OAUTH_TOKEN);
				})
				.onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
					throw new ApiException(ErrorCode.OAUTH_PROVIDER_ERROR);
				})
				.body(JSON_OBJECT);
		} catch (ResourceAccessException e) {
			throw new ApiException(ErrorCode.OAUTH_PROVIDER_ERROR);
		}
		if (body == null) {
			throw new ApiException(ErrorCode.OAUTH_PROVIDER_ERROR);
		}
		return body;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> nested(Map<String, Object> source, String key) {
		if (source == null) {
			return null;
		}
		Object value = source.get(key);
		return value instanceof Map ? (Map<String, Object>) value : null;
	}

	private static String text(Map<String, Object> source, String key) {
		if (source == null) {
			return null;
		}
		Object value = source.get(key);
		return value instanceof String text ? text : null;
	}

	private static Long toLong(Object value) {
		return value instanceof Number number ? number.longValue() : null;
	}
}
