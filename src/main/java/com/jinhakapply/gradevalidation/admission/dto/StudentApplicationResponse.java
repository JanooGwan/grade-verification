package com.jinhakapply.gradevalidation.admission.dto;

import java.time.LocalDateTime;

import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;

public record StudentApplicationResponse(
    Long id,
    Long studentId,
    Long universityId,
    String universityName,
    Long admissionTrackId,
    String admissionTrackName,
    int admissionYear,
    Long recruitmentUnitId,
    String recruitmentUnitCode,
    String recruitmentUnitName,
    LocalDateTime createdAt
) {
    public static StudentApplicationResponse from(StudentApplication application) {
        var unit = application.getRecruitmentUnit();
        var track = unit.getAdmissionTrack();
        return new StudentApplicationResponse(
            application.getId(), application.getStudent().getId(), track.getUniversity().getId(),
            track.getUniversity().getName(), track.getId(), track.getName(), track.getAdmissionYear(),
            unit.getId(), unit.getCode(), unit.getName(), application.getCreatedAt()
        );
    }
}
