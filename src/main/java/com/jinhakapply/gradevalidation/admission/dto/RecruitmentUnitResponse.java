package com.jinhakapply.gradevalidation.admission.dto;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;

public record RecruitmentUnitResponse(
    Long id,
    Long admissionTrackId,
    String code,
    String name,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static RecruitmentUnitResponse from(RecruitmentUnit unit) {
        return new RecruitmentUnitResponse(
            unit.getId(), unit.getAdmissionTrack().getId(), unit.getCode(), unit.getName(),
            unit.isActive(), unit.getCreatedAt(), unit.getUpdatedAt()
        );
    }
}
