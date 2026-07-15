package com.jinhakapply.gradevalidation.admission.controller;

import java.util.List;

import com.jinhakapply.gradevalidation.admission.dto.AdmissionTrackResponse;
import com.jinhakapply.gradevalidation.admission.dto.ApplicationVerificationResponse;
import com.jinhakapply.gradevalidation.admission.dto.CreateAdmissionTrackRequest;
import com.jinhakapply.gradevalidation.admission.dto.CreateRecruitmentUnitRequest;
import com.jinhakapply.gradevalidation.admission.dto.CreateStudentApplicationRequest;
import com.jinhakapply.gradevalidation.admission.dto.RecruitmentUnitResponse;
import com.jinhakapply.gradevalidation.admission.dto.RuleMatchResponse;
import com.jinhakapply.gradevalidation.admission.dto.StudentApplicationResponse;
import com.jinhakapply.gradevalidation.admission.dto.UpdateAdmissionTrackRequest;
import com.jinhakapply.gradevalidation.admission.dto.UpdateRecruitmentUnitRequest;
import com.jinhakapply.gradevalidation.admission.dto.VerificationHistoryResponse;
import com.jinhakapply.gradevalidation.admission.dto.VerificationHistoryDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Admissions", description = "전형·모집단위·학생 지원 및 자동 성적 검증 API")
@RequestMapping("/api/admissions")
@Validated
public interface AdmissionApi {
    @Operation(summary = "전형 등록")
    @PostMapping("/tracks")
    ResponseEntity<AdmissionTrackResponse> createTrack(@Valid @RequestBody CreateAdmissionTrackRequest request);

    @Operation(summary = "전형 수정")
    @PutMapping("/tracks/{trackId}")
    ResponseEntity<AdmissionTrackResponse> updateTrack(
        @PathVariable Long trackId,
        @Valid @RequestBody UpdateAdmissionTrackRequest request
    );

    @Operation(summary = "대학·모집연도별 전형과 모집단위 조회")
    @GetMapping("/tracks")
    ResponseEntity<List<AdmissionTrackResponse>> findTracks(
        @RequestParam @NotNull Long universityId,
        @RequestParam @Min(2000) @Max(2100) int admissionYear
    );

    @Operation(summary = "모집단위 등록")
    @PostMapping("/tracks/{trackId}/recruitment-units")
    ResponseEntity<RecruitmentUnitResponse> createUnit(
        @PathVariable Long trackId,
        @Valid @RequestBody CreateRecruitmentUnitRequest request
    );

    @Operation(summary = "모집단위 수정")
    @PutMapping("/recruitment-units/{unitId}")
    ResponseEntity<RecruitmentUnitResponse> updateUnit(
        @PathVariable Long unitId,
        @Valid @RequestBody UpdateRecruitmentUnitRequest request
    );

    @Operation(summary = "학생 지원 정보 등록")
    @PostMapping("/students/{studentId}/applications")
    ResponseEntity<StudentApplicationResponse> createApplication(
        @PathVariable Long studentId,
        @Valid @RequestBody CreateStudentApplicationRequest request
    );

    @Operation(summary = "학생 지원 정보 조회")
    @GetMapping("/students/{studentId}/applications")
    ResponseEntity<List<StudentApplicationResponse>> findApplications(@PathVariable Long studentId);

    @Operation(summary = "학생 지원 정보 삭제")
    @DeleteMapping("/students/{studentId}/applications/{applicationId}")
    ResponseEntity<Void> deleteApplication(
        @PathVariable Long studentId,
        @PathVariable Long applicationId
    );

    @Operation(summary = "지원 정보에 적용할 규칙 자동 조회")
    @GetMapping("/students/{studentId}/applications/{applicationId}/rule-match")
    ResponseEntity<RuleMatchResponse> matchRule(
        @PathVariable Long studentId,
        @PathVariable Long applicationId
    );

    @Operation(summary = "지원 정보 기준 학생 성적 자동 검증")
    @PostMapping("/students/{studentId}/applications/{applicationId}/verify")
    ResponseEntity<ApplicationVerificationResponse> verifyApplication(
        @PathVariable Long studentId,
        @PathVariable Long applicationId
    );

    @Operation(summary = "학생 성적 검증 이력 조회")
    @GetMapping("/students/{studentId}/verifications")
    ResponseEntity<List<VerificationHistoryResponse>> findVerificationHistory(@PathVariable Long studentId);

    @Operation(summary = "학생 성적 검증 스냅샷 상세 조회")
    @GetMapping("/students/{studentId}/verifications/{runId}")
    ResponseEntity<VerificationHistoryDetailResponse> findVerificationHistoryDetail(
        @PathVariable Long studentId,
        @PathVariable Long runId
    );
}
