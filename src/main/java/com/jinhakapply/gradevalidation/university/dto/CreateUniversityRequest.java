package com.jinhakapply.gradevalidation.university.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUniversityRequest(
		@NotBlank(message = "대학 코드는 필수입니다.")
		@Size(max = 20, message = "대학 코드는 20자 이하여야 합니다.")
		@Pattern(
				regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$",
				message = "대학 코드는 영문, 숫자, 밑줄, 하이픈만 사용할 수 있습니다."
		)
		String code,

		@NotBlank(message = "대학명은 필수입니다.")
		@Size(max = 100, message = "대학명은 100자 이하여야 합니다.")
		String name
) {
}
