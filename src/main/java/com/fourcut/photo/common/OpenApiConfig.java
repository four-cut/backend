package com.fourcut.photo.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	// 전역 addSecurityItem 은 일부러 넣지 않는다. 그러면 로그인 없이 쓰는 포토부스 API 까지
	// 인증 필요로 표시되어 문서가 사실과 달라진다. 인증이 필요한 컨트롤러에만
	// @SecurityRequirement 를 붙인다.
	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("네컷사진 API")
				.description("포토부스 촬영 및 소셜 로그인 API")
				.version("v1"))
			.components(new Components()
				.addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
					.name(BEARER_SCHEME)
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.in(SecurityScheme.In.HEADER)));
	}
}
