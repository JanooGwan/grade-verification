package com.jinhakapply.gradevalidation.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI gradeValidationOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Grade Validation API")
						.description("대학별 입학 성적 검증 시스템 API")
						.version("v1"));
	}
}
