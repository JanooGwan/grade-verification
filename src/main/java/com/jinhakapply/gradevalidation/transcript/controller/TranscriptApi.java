package com.jinhakapply.gradevalidation.transcript.controller;

import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentTranscriptResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportSummaryResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentCommonDataRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpsertTranscriptCourseRequest;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Student transcripts", description = "학생 및 공통 학생부 교과성적 관리 API")
@RequestMapping("/api/transcripts")
@Validated
public interface TranscriptApi {

    @Operation(
        summary = "학생부 Excel 가져오기",
        description = "첫 번째 시트의 한 행을 한 과목으로 읽습니다. "
            + "필수 헤더는 지원자번호, 학생명, 학년, 학기, 교과, 과목명, 이수단위입니다. "
            + "석차등급 또는 성취도 중 하나가 필요합니다."
    )
    @PostMapping(value = "/imports/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<TranscriptImportResponse> importExcel(
        @RequestParam int admissionYear,
        @RequestParam(defaultValue = "VALID_ROWS_ONLY") TranscriptImportMode mode,
        @RequestParam(required = false) Long universityId,
        @Parameter(description = ".xlsx 또는 .xls, 최대 40MB")
        @RequestPart("file") MultipartFile file
    );

    @Operation(summary = "학생부 Excel 저장 전 미리보기")
    @PostMapping(value = "/imports/excel/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<TranscriptPreviewResponse> previewExcel(
        @RequestParam int admissionYear,
        @RequestParam(required = false) Long universityId,
        @RequestPart("file") MultipartFile file
    );

    @Operation(summary = "학생부 Excel 업로드 양식 다운로드")
    @GetMapping(value = "/imports/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ResponseEntity<byte[]> downloadTemplate();

    @Operation(summary = "최근 학생부 가져오기 이력 조회")
    @GetMapping("/imports")
    ResponseEntity<List<TranscriptImportSummaryResponse>> findImports();

    @Operation(summary = "학생부 가져오기 이력 조회")
    @GetMapping("/imports/{importId}")
    ResponseEntity<TranscriptImportSummaryResponse> findImport(@PathVariable Long importId);

    @Operation(summary = "지원자 목록 조회", description = "지원번호, 학생명, 고등학교명으로 검색할 수 있습니다.")
    @GetMapping("/students")
    ResponseEntity<StudentPageResponse> findStudents(
        @RequestParam("admissionYear") @Min(2000) @Max(2100) int admissionYear,
        @RequestParam(value = "keyword", required = false) @Size(max = 100) String keyword,
        @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
        @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size
    );

    @Operation(summary = "지원자 학생부 조회")
    @GetMapping("/students/{applicantNumber}")
    ResponseEntity<StudentTranscriptResponse> findStudentTranscript(
        @PathVariable("applicantNumber") String applicantNumber,
        @RequestParam("admissionYear") int admissionYear
    );

    @Operation(summary = "학생 기본정보 수정")
    @PutMapping("/students/{studentId}")
    ResponseEntity<StudentTranscriptResponse> updateStudent(
        @PathVariable Long studentId,
        @Valid @RequestBody UpdateStudentRequest request
    );

    @Operation(summary = "대학 공통 지원자 평가 데이터 수정",
        description = "학력·졸업 상태, 학년별 출결과 학교폭력 조치 원천데이터를 한 번 등록해 모든 대학 전형 계산에서 사용합니다.")
    @PutMapping("/students/{studentId}/common-data")
    ResponseEntity<StudentTranscriptResponse> updateStudentCommonData(
        @PathVariable Long studentId,
        @Valid @RequestBody UpdateStudentCommonDataRequest request
    );

    @Operation(summary = "학생 및 학생부 데이터 삭제")
    @DeleteMapping("/students/{studentId}")
    ResponseEntity<Void> deleteStudent(@PathVariable Long studentId);

    @Operation(summary = "학생부 과목 추가")
    @PostMapping("/students/{studentId}/courses")
    ResponseEntity<StudentTranscriptResponse.CourseResponse> createCourse(
        @PathVariable Long studentId,
        @Valid @RequestBody UpsertTranscriptCourseRequest request
    );

    @Operation(summary = "학생부 과목 수정")
    @PutMapping("/students/{studentId}/courses/{courseId}")
    ResponseEntity<StudentTranscriptResponse.CourseResponse> updateCourse(
        @PathVariable Long studentId,
        @PathVariable Long courseId,
        @Valid @RequestBody UpsertTranscriptCourseRequest request
    );

    @Operation(summary = "학생부 과목 삭제")
    @DeleteMapping("/students/{studentId}/courses/{courseId}")
    ResponseEntity<Void> deleteCourse(@PathVariable Long studentId, @PathVariable Long courseId);
}
