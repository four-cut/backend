package com.fourcut.photo.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 애플 토큰 엔드포인트에 낼 client secret 을 제대로 만드는지 본다.
 *
 * 실제 .p8 키 없이 검증하려고 테스트에서 P-256 키쌍을 만들어 쓴다. 애플이 요구하는 규격은
 * 공개돼 있으므로, 우리가 만든 JWT 가 그 규격과 맞는지와 서명이 실제로 검증되는지를 본다.
 */
class AppleClientSecretTest {

	private static final String TEAM_ID = "9XP9Q9NGAM";
	private static final String KEY_ID = "ABCD123456";
	private static final String CLIENT_ID = "com.fourcut.app";

	private static KeyPair keyPair;
	private static String pem;

	@BeforeAll
	static void generateKey() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		keyPair = generator.generateKeyPair();
		pem = "-----BEGIN PRIVATE KEY-----\n"
			+ Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
			+ "\n-----END PRIVATE KEY-----\n";
	}

	private static SignedJWT parse(String pemKey) throws Exception {
		return SignedJWT.parse(new AppleClientSecret(TEAM_ID, KEY_ID, pemKey).generate(CLIENT_ID));
	}

	@Test
	void 애플이_요구하는_헤더로_서명한다() throws Exception {
		SignedJWT jwt = parse(pem);

		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
		// kid 가 없으면 애플이 어느 키로 검증할지 모른다.
		assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
	}

	@Test
	void 애플이_요구하는_클레임을_담는다() throws Exception {
		SignedJWT jwt = parse(pem);

		assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
		// sub 는 Team ID 가 아니라 클라이언트다. 헷갈리기 쉬운 자리다.
		assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
		assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(AppleClientSecret.AUDIENCE);
	}

	@Test
	void 만료_시각이_미래이고_길지_않다() throws Exception {
		SignedJWT jwt = parse(pem);

		Instant expiresAt = jwt.getJWTClaimsSet().getExpirationTime().toInstant();
		assertThat(expiresAt).isAfter(Instant.now());
		// 애플은 6개월까지 허용하지만 한 번 쓰고 버리므로 짧아야 한다.
		assertThat(expiresAt).isBefore(Instant.now().plusSeconds(3600));
	}

	@Test
	void 그_키의_공개키로_서명이_검증된다() throws Exception {
		SignedJWT jwt = parse(pem);

		assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
	}

	// 환경 변수로 넘기면 줄바꿈이 사라지는 일이 흔하다. 그래도 읽혀야 한다.
	@Test
	void 줄바꿈과_머리말이_없어도_키를_읽는다() throws Exception {
		String flattened = pem
			.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s", "");

		assertThat(parse(flattened).verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
	}

	// 키가 잘못되면 탈퇴할 때가 아니라 설정을 읽는 시점에 알아야 한다.
	@Test
	void 잘못된_키는_읽는_즉시_알려준다() {
		assertThatThrownBy(() -> new AppleClientSecret(TEAM_ID, KEY_ID, "not-a-key"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("private-key");
	}
}
