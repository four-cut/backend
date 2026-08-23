package com.fourcut.photo.member;

import com.fourcut.photo.auth.MemberPrincipal;
import com.fourcut.photo.member.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "내 정보 조회 및 탈퇴 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@Operation(summary = "내 정보 조회")
	@GetMapping("/me")
	public MemberResponse getMe(@AuthenticationPrincipal MemberPrincipal principal) {
		return memberService.getMe(principal.memberId());
	}

	@Operation(summary = "회원 탈퇴",
		description = "계정을 탈퇴 처리하고 모든 리프레시 토큰을 무효화한 뒤 소셜 계정 연결을 해제합니다.")
	@DeleteMapping("/me")
	public ResponseEntity<Void> withdraw(@AuthenticationPrincipal MemberPrincipal principal) {
		memberService.withdraw(principal.memberId());
		return ResponseEntity.noContent().build();
	}
}
