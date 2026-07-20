package com.jinhakapply.gradevalidation.admission.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;

public record AdmissionTrackResponse(
    Long id,
    Long universityId,
    String universityName,
    int admissionYear,
    String name,
    boolean active,
    List<RecruitmentUnitResponse> recruitmentUnits,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AdmissionTrackResponse of(AdmissionTrack track, List<RecruitmentUnit> units) {
        return new AdmissionTrackResponse(
            track.getId(), track.getUniversity().getId(), track.getUniversity().getName(),
            track.getAdmissionYear(), track.getName(), track.isActive(),
            units.stream().map(RecruitmentUnitResponse::from).toList(),
            track.getCreatedAt(), track.getUpdatedAt()
        );
    }
}
