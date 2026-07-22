package com.jinhakapply.gradevalidation.transcript.controller;

import java.net.URI;

import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentTranscriptResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportSummaryResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentCommonDataRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpsertTranscriptCourseRequest;
import com.jinhakapply.gradevalidation.transcript.service.TranscriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class TranscriptController implements TranscriptApi {

    private final TranscriptService transcriptService;

    @Override
    public ResponseEntity<TranscriptImportResponse> importExcel(
        int admissionYear,
        TranscriptImportMode mode,
        Long universityId,
        MultipartFile file
    ) {
        TranscriptImportResponse response = transcriptService.importExcel(admissionYear, mode, universityId, file);
        return ResponseEntity
            .created(URI.create("/api/transcripts/imports/" + response.importId()))
            .body(response);
    }

    @Override
    public ResponseEntity<TranscriptPreviewResponse> previewExcel(
        int admissionYear,
        Long universityId,
        MultipartFile file
    ) {
        return ResponseEntity.ok(transcriptService.previewExcel(admissionYear, universityId, file));
    }

    @Override
    public ResponseEntity<byte[]> downloadTemplate() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student-transcript-template.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(transcriptService.createExcelTemplate());
    }

    @Override
    public ResponseEntity<List<TranscriptImportSummaryResponse>> findImports() {
        return ResponseEntity.ok(transcriptService.findImports());
    }

    @Override
    public ResponseEntity<TranscriptImportSummaryResponse> findImport(Long importId) {
        return ResponseEntity.ok(transcriptService.findImport(importId));
    }

    @Override
    public ResponseEntity<StudentPageResponse> findStudents(
        int admissionYear,
        String keyword,
        int page,
        int size
    ) {
        return ResponseEntity.ok(transcriptService.findStudents(admissionYear, keyword, page, size));
    }

    @Override
    public ResponseEntity<StudentTranscriptResponse> findStudentTranscript(
        String applicantNumber,
        int admissionYear
    ) {
        return ResponseEntity.ok(transcriptService.findStudentTranscript(admissionYear, applicantNumber));
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
