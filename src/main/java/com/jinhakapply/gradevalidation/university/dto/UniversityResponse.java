package com.jinhakapply.gradevalidation.university.dto;

import java.time.Instant;

import com.jinhakapply.gradevalidation.university.domain.University;

public record UniversityResponse(
		Long id,
		String code,
		String name,
		boolean active,
		Instant createdAt,
		Instant updatedAt
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
