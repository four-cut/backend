package com.fourcut.photo.auth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;

// 폐기된 리프레시 토큰이 다시 제시된 경우. 해당 회원의 토큰을 전부 무효화해야 하는데
// 그 작업은 이 예외로 롤백되면 안 되므로, 트랜잭션 밖에 있는 AuthService 가 처리한다.
public class RefreshTokenReuseException extends ApiException {

	private final Long memberId;

	public RefreshTokenReuseException(Long memberId) {
		super(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
		this.memberId = memberId;
	}

	public Long getMemberId() {
		return memberId;
	}
}
