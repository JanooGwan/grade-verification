package com.jinhakapply.gradevalidation.university.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUniversityRequest(
		@NotBlank(message = "대학명은 필수입니다.")
		@Size(max = 100, message = "대학명은 100자 이하여야 합니다.")
		String name,

		@NotNull(message = "사용 여부는 필수입니다.")
		Boolean active
) {
}
