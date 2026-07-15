package com.jinhakapply.gradevalidation.university.dto;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.university.domain.University;

public record UniversityResponse(
		Long id,
		String code,
		String name,
		boolean active,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static UniversityResponse from(University university) {
		return new UniversityResponse(
				university.getId(),
				university.getCode(),
				university.getName(),
				university.isActive(),
				university.getCreatedAt(),
				university.getUpdatedAt()
		);
	}
}
