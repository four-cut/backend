package com.fourcut.photo.auth.oauth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// APPLE 은 enum 에만 있고 구현체가 없다. 맵 조회가 비면 그대로 UNSUPPORTED_OAUTH_PROVIDER 가 되므로
// 같은 결과를 내는 스텁 클래스를 따로 두지 않는다. 나중에 OAuthClient 구현 하나만 추가하면 붙는다.
@Component
public class OAuthClientRegistry {

	private final Map<OAuthProvider, OAuthClient> clients;

	public OAuthClientRegistry(List<OAuthClient> clients) {
		this.clients = clients.stream()
			.collect(Collectors.toUnmodifiableMap(OAuthClient::provider, Function.identity()));
	}

	public OAuthClient resolve(OAuthProvider provider) {
		OAuthClient client = clients.get(provider);
		if (client == null) {
			throw new ApiException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
		}
		return client;
	}

	public void unlinkQuietly(OAuthProvider provider, String providerId) {
		OAuthClient client = clients.get(provider);
		if (client != null) {
			client.unlink(providerId);
		}
	}
}
