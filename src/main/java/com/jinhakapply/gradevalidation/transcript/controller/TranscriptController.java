package com.jinhakapply.gradevalidation.transcript.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentTranscriptResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportSummaryResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationPersistenceResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationDetailResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SourceImportStartResponse;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentCommonDataRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpsertTranscriptCourseRequest;
import com.jinhakapply.gradevalidation.transcript.service.TranscriptService;
import com.jinhakapply.gradevalidation.transcript.service.SyuSourceImportService;
import com.jinhakapply.gradevalidation.transcript.service.StoredTranscriptVerificationService;
import com.jinhakapply.gradevalidation.transcript.service.SavedVerificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
public class TranscriptController implements TranscriptApi {

    private final TranscriptService transcriptService;
    private final SyuSourceImportService syuSourceImportService;
    private final StoredTranscriptVerificationService storedTranscriptVerificationService;
    private final SavedVerificationQueryService savedVerificationQueryService;

    @Override
    public ResponseEntity<SourceImportStartResponse> importSyuSourceExcel(
        int admissionYear, Long universityId, MultipartFile file
    ) {
        return ResponseEntity.accepted().body(syuSourceImportService.queue(admissionYear, universityId, file));
    }

    @Override
    public ResponseEntity<TranscriptImportResponse> importExcel(
        int admissionYear,
        TranscriptImportMode mode,
        Long universityId,
        MultipartFile file,
        MultipartFile schoolInfoFile,
        MultipartFile vocationalTrainingFile
    ) {
        TranscriptImportResponse response = transcriptService.importExcel(
            admissionYear, mode, universityId, file, schoolInfoFile, vocationalTrainingFile
        );
        return ResponseEntity
            .created(URI.create("/api/transcripts/imports/" + response.importId()))
            .body(response);
    }

    @Override
    public ResponseEntity<TranscriptPreviewResponse> verifyStoredTranscript(
        Long universityId, int admissionYear
    ) {
        return ResponseEntity.ok(storedTranscriptVerificationService.verify(universityId, admissionYear));
    }

    @Override
    public ResponseEntity<StoredVerificationPersistenceResponse> persistStoredTranscriptVerification(
        Long universityId, int admissionYear
    ) {
        return ResponseEntity.ok(storedTranscriptVerificationService.persist(universityId, admissionYear));
    }

    @Override
    public ResponseEntity<List<SavedVerificationBatchResponse>> findSavedVerificationBatches(
        Long universityId, int admissionYear
    ) {
        return ResponseEntity.ok(savedVerificationQueryService.findBatches(universityId, admissionYear));
    }

    @Override
    public ResponseEntity<SavedVerificationPageResponse> findSavedVerificationResults(
        Long sourceImportId, String keyword, int page, int size
    ) {
        return ResponseEntity.ok(savedVerificationQueryService.findResults(sourceImportId, keyword, page, size));
    }

    @Override
    public ResponseEntity<SavedVerificationDetailResponse> findSavedVerificationDetail(Long verificationRunId) {
        return ResponseEntity.ok(savedVerificationQueryService.findDetail(verificationRunId));
    }

    @Override
    public ResponseEntity<StreamingResponseBody> exportSavedVerificationBatch(Long sourceImportId) {
        StreamingResponseBody result = output -> {
            output.flush();
            output.write(savedVerificationQueryService.export(sourceImportId));
        };
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("저장검증결과-" + sourceImportId + ".xlsx", StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(result);
    }

    @Override
    public ResponseEntity<StreamingResponseBody> exportStoredTranscriptVerification(
        Long universityId,
        int admissionYear
    ) {
        StreamingResponseBody result = output ->
            storedTranscriptVerificationService.writeExport(universityId, admissionYear, output);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("DB-성적검증결과.xlsx", StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(result);
    }

    @Override
    public ResponseEntity<byte[]> downloadTemplate() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student-transcript-template.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(transcriptService.createExcelTemplate());
    }

    @Override
    public ResponseEntity<List<TranscriptImportSummaryResponse>> findImports(Long universityId) {
        return ResponseEntity.ok(transcriptService.findImports(universityId));
    }

    @Override
    public ResponseEntity<TranscriptImportSummaryResponse> findImport(Long importId) {
        return ResponseEntity.ok(transcriptService.findImport(importId));
    }

    @Override
    public ResponseEntity<StreamingResponseBody> downloadImportResult(Long importId) {
        StreamingResponseBody result = output -> transcriptService.writeImportResult(importId, output);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("가져오기-" + importId + "-환산결과.xlsx", StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(result);
    }

    @Override
    public ResponseEntity<StudentPageResponse> findStudents(
        Long universityId,
        int admissionYear,
        String keyword,
        int page,
        int size
    ) {
        return ResponseEntity.ok(transcriptService.findStudents(universityId, admissionYear, keyword, page, size));
    }

    @Override
    public ResponseEntity<StudentTranscriptResponse> findStudentTranscript(
        String applicantNumber,
        Long universityId,
        int admissionYear
    ) {
        return ResponseEntity.ok(transcriptService.findStudentTranscript(universityId, admissionYear, applicantNumber));
    }

    @Override
    public ResponseEntity<StudentTranscriptResponse> updateStudent(Long studentId, UpdateStudentRequest request) {
        return ResponseEntity.ok(transcriptService.updateStudent(studentId, request));
    }

    @Override
    public ResponseEntity<StudentTranscriptResponse> updateStudentCommonData(
        Long studentId, UpdateStudentCommonDataRequest request
    ) {
        return ResponseEntity.ok(transcriptService.updateStudentCommonData(studentId, request));
    }

    @Override
    public ResponseEntity<Void> deleteStudent(Long studentId) {
        transcriptService.deleteStudent(studentId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<StudentTranscriptResponse.CourseResponse> createCourse(
        Long studentId,
        UpsertTranscriptCourseRequest request
    ) {
        return ResponseEntity.status(201).body(transcriptService.createCourse(studentId, request));
    }

    @Override
    public ResponseEntity<StudentTranscriptResponse.CourseResponse> updateCourse(
        Long studentId,
        Long courseId,
        UpsertTranscriptCourseRequest request
    ) {
        return ResponseEntity.ok(transcriptService.updateCourse(studentId, courseId, request));
    }

    @Override
    public ResponseEntity<Void> deleteCourse(Long studentId, Long courseId) {
        transcriptService.deleteCourse(studentId, courseId);
        return ResponseEntity.noContent().build();
    }
}
