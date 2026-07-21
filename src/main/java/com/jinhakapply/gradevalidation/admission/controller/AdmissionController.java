package com.jinhakapply.gradevalidation.admission.controller;

import java.net.URI;
import java.util.List;

import com.jinhakapply.gradevalidation.admission.dto.AdmissionTrackResponse;
import com.jinhakapply.gradevalidation.admission.dto.ApplicationVerificationResponse;
import com.jinhakapply.gradevalidation.admission.dto.ApplicationScoreResponse;
import com.jinhakapply.gradevalidation.admission.dto.CalculateApplicationScoreRequest;
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
import com.jinhakapply.gradevalidation.admission.service.AdmissionService;
import com.jinhakapply.gradevalidation.admission.service.ApplicationScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdmissionController implements AdmissionApi {
    private final AdmissionService admissionService;
    private final ApplicationScoreService applicationScoreService;

    @Override
    public ResponseEntity<AdmissionTrackResponse> createTrack(CreateAdmissionTrackRequest request) {
        AdmissionTrackResponse response = admissionService.createTrack(request);
        return ResponseEntity.created(URI.create("/api/admissions/tracks/" + response.id())).body(response);
    }

    @Override
    public ResponseEntity<AdmissionTrackResponse> updateTrack(Long trackId, UpdateAdmissionTrackRequest request) {
        return ResponseEntity.ok(admissionService.updateTrack(trackId, request));
    }

    @Override
    public ResponseEntity<List<AdmissionTrackResponse>> findTracks(Long universityId, int admissionYear) {
        return ResponseEntity.ok(admissionService.findTracks(universityId, admissionYear));
    }

    @Override
    public ResponseEntity<RecruitmentUnitResponse> createUnit(Long trackId, CreateRecruitmentUnitRequest request) {
        RecruitmentUnitResponse response = admissionService.createUnit(trackId, request);
        return ResponseEntity.created(URI.create("/api/admissions/recruitment-units/" + response.id())).body(response);
    }

    @Override
    public ResponseEntity<RecruitmentUnitResponse> updateUnit(Long unitId, UpdateRecruitmentUnitRequest request) {
        return ResponseEntity.ok(admissionService.updateUnit(unitId, request));
    }

    @Override
    public ResponseEntity<StudentApplicationResponse> createApplication(
        Long studentId,
        CreateStudentApplicationRequest request
    ) {
        StudentApplicationResponse response = admissionService.createApplication(studentId, request);
        return ResponseEntity.created(URI.create(
            "/api/admissions/students/" + studentId + "/applications/" + response.id()
        )).body(response);
    }

    @Override
    public ResponseEntity<List<StudentApplicationResponse>> findApplications(Long studentId) {
        return ResponseEntity.ok(admissionService.findApplications(studentId));
    }

    @Override
    public ResponseEntity<Void> deleteApplication(Long studentId, Long applicationId) {
        admissionService.deleteApplication(studentId, applicationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RuleMatchResponse> matchRule(Long studentId, Long applicationId) {
        return ResponseEntity.ok(admissionService.matchRule(studentId, applicationId));
    }

    @Override
    public ResponseEntity<ApplicationVerificationResponse> verifyApplication(Long studentId, Long applicationId) {
        return ResponseEntity.ok(admissionService.verifyApplication(studentId, applicationId));
    }

    @Override
    public ResponseEntity<ApplicationScoreResponse> calculateApplicationScore(
        Long studentId,
        Long applicationId,
        CalculateApplicationScoreRequest request
    ) {
        return ResponseEntity.ok(applicationScoreService.calculate(studentId, applicationId, request));
    }

    @Override
    public ResponseEntity<List<VerificationHistoryResponse>> findVerificationHistory(Long studentId) {
        return ResponseEntity.ok(admissionService.findVerificationHistory(studentId));
    }

    @Override
    public ResponseEntity<VerificationHistoryDetailResponse> findVerificationHistoryDetail(Long studentId, Long runId) {
        return ResponseEntity.ok(admissionService.findVerificationHistoryDetail(studentId, runId));
    }
}
