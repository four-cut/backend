package com.fourcut.photo.auth.oauth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 구현체는 스프링이 주입하는 목록에서 모은다. 맵에 없는 제공자는 UNSUPPORTED_OAUTH_PROVIDER 다.
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

	public void unlinkQuietly(OAuthProvider provider, OAuthUnlinkCommand command) {
		OAuthClient client = clients.get(provider);
		if (client != null) {
			client.unlink(command);
		}
	}
}
