package com.fourcut.photo.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 애플 토큰 엔드포인트에 낼 client secret 을 만든다.
 *
 * 카카오의 관리자 키나 구글의 클라이언트 시크릿처럼 고정 문자열이 아니라, 우리가 직접
 * 서명해서 만드는 짧은 수명의 JWT 다. 서명에는 애플 개발자 콘솔에서 받은 .p8 키가 필요하다.
 *
 * 애플 규격: 헤더는 ES256 에 kid 로 Key ID, 본문은 iss=Team ID, sub=client_id(번들 ID),
 * aud=애플 토큰 서버, 그리고 iat/exp. exp 는 최대 6개월까지 허용되지만 길게 둘 이유가 없다 —
 * 서명이 한 번에 몇 마이크로초라 쓸 때마다 새로 만든다.
 */
public class AppleClientSecret {

	static final String AUDIENCE = "https://appleid.apple.com";

	// 발급 직후 한 번 쓰고 버린다. 서버와 애플의 시계가 조금 어긋나도 괜찮을 만큼만 둔다.
	private static final long LIFETIME_SECONDS = 300;

	private final String teamId;
	private final String keyId;
	private final PrivateKey privateKey;

	public AppleClientSecret(String teamId, String keyId, String privateKeyPem) {
		this.teamId = teamId;
		this.keyId = keyId;
		this.privateKey = parsePkcs8(privateKeyPem);
	}

	/** @param clientId 코드가 발급된 클라이언트. iOS 앱이면 번들 ID 다. */
	public String generate(String clientId) {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.issuer(teamId)
			.subject(clientId)
			.audience(AUDIENCE)
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plusSeconds(LIFETIME_SECONDS)))
			.build();

		// kid 가 없으면 애플이 어느 키로 검증할지 모른다.
		SignedJWT jwt = new SignedJWT(
			new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(),
			claims);
		try {
			jwt.sign(new ECDSASigner(privateKey, com.nimbusds.jose.jwk.Curve.P_256));
		} catch (JOSEException e) {
			throw new IllegalStateException("애플 client secret 서명에 실패했습니다", e);
		}
		return jwt.serialize();
	}

	/**
	 * .p8 파일 내용을 개인키로 읽는다.
	 *
	 * 환경 변수로 넘기면 줄바꿈이 살아남기도 하고 사라지기도 해서, PEM 머리말·꼬리말과
	 * 모든 공백을 걷어내고 본문만 디코딩한다. 어느 쪽으로 들어와도 똑같이 읽힌다.
	 */
	private static PrivateKey parsePkcs8(String pem) {
		String base64 = pem
			.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s", "");
		try {
			return KeyFactory.getInstance("EC")
				.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
			// 키가 잘못되면 기동 시점에 바로 알아야 한다. 탈퇴할 때 처음 터지면 늦다.
			throw new IllegalStateException(
				"애플 개인키(.p8)를 읽지 못했습니다. fourcut.oauth.apple.private-key 를 확인하세요", e);
		}
	}
}
