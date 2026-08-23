package com.fourcut.photo.member;

import com.fourcut.photo.auth.oauth.OAuthClientRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 커밋 이후에 실행한다. 제공자 연결 해제는 외부 HTTP 호출이라 트랜잭션 안에서 하면
// DB 커넥션을 네트워크 대기 시간만큼 붙잡고, 실패하면 탈퇴까지 롤백된다.
@Component
public class MemberWithdrawnListener {

	private final OAuthClientRegistry oAuthClientRegistry;

	public MemberWithdrawnListener(OAuthClientRegistry oAuthClientRegistry) {
		this.oAuthClientRegistry = oAuthClientRegistry;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void unlinkFromProvider(MemberWithdrawnEvent event) {
		oAuthClientRegistry.unlinkQuietly(event.provider(), event.providerId());
	}
}
