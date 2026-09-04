package com.jinhakapply.gradevalidation.transcript.controller;

import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentTranscriptResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportSummaryResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StoredVerificationPersistenceResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationBatchResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationDetailResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationExportStartResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SavedVerificationExportStatusResponse;
import com.jinhakapply.gradevalidation.transcript.dto.SourceImportStartResponse;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "Student transcripts", description = "학생 및 공통 학생부 교과성적 관리 API")
@RequestMapping("/api/transcripts")
@Validated
public interface TranscriptApi {

    @Operation(
        summary = "삼육대 대용량 원천 Excel 가져오기",
        description = "학생부 교과 성적·학생부출결 시트를 스트리밍으로 읽고 백그라운드에서 배치 저장합니다."
    )
    @PostMapping(value = "/imports/source/syu", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<SourceImportStartResponse> importSyuSourceExcel(
        @RequestParam int admissionYear,
        @RequestParam @NotNull @Positive Long universityId,
        @Parameter(description = "삼육대 원천 .xlsx, 최대 200MB")
        @RequestPart("file") MultipartFile file
    );

    @Operation(
        summary = "명지전문대 원천 CSV 가져오기",
        description = "2026학년도 지원자정보·학생부기본정보·교과학습발달상황 CSV를 스트리밍으로 가져옵니다."
    )
    @PostMapping(value = "/imports/source/mjc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<SourceImportStartResponse> importMjcSourceCsv(
        @RequestParam @Min(2000) @Max(2100) int admissionYear,
        @RequestParam @NotNull @Positive Long universityId,
        @RequestPart("applicantsFile") MultipartFile applicantsFile,
        @RequestPart("baseInfoFile") MultipartFile baseInfoFile,
        @RequestPart("subjectScoreFile") MultipartFile subjectScoreFile
    );

    @Operation(
        summary = "명지전문대 통합 Excel 가져오기",
        description = "2026학년도 원천 CSV를 시트별로 묶은 통합 .xlsx를 스트리밍으로 가져옵니다."
    )
    @PostMapping(value = "/imports/source/mjc/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<SourceImportStartResponse> importMjcSourceExcel(
        @RequestParam @Min(2000) @Max(2100) int admissionYear,
        @RequestParam @NotNull @Positive Long universityId,
        @RequestPart("file") MultipartFile file
    );

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
        @RequestParam @NotNull @Positive Long universityId,
        @Parameter(description = ".xlsx 또는 .xls, 최대 40MB")
        @RequestPart("file") MultipartFile file,
        @Parameter(description = "선택: 수험번호별 출신고교 추가정보 Excel")
        @RequestPart(value = "schoolInfoFile", required = false) MultipartFile schoolInfoFile,
        @Parameter(description = "선택: 경복대 직업과정 위탁생의 학기별 이수정보 Excel")
        @RequestPart(value = "vocationalTrainingFile", required = false) MultipartFile vocationalTrainingFile
    );

    @Operation(summary = "DB 저장 학생부 성적검증", description = "최신 완료 가져오기의 DB 데이터만 사용합니다.")
    @GetMapping("/verifications")
    ResponseEntity<TranscriptPreviewResponse> verifyStoredTranscript(
        @RequestParam @NotNull @Positive Long universityId,
        @RequestParam @Min(2000) @Max(2100) int admissionYear
    );

    @Operation(summary = "DB 저장 학생부 성적검증 결과 저장", description = "최신 완료 업로드 건의 검증 결과를 DB에 저장합니다.")
    @PostMapping("/verifications/persist")
    ResponseEntity<StoredVerificationPersistenceResponse> persistStoredTranscriptVerification(
        @RequestParam @NotNull @Positive Long universityId,
        @RequestParam @Min(2000) @Max(2100) int admissionYear
    );

    @Operation(summary = "저장된 일괄 성적검증 회차 목록 조회")
    @GetMapping("/saved-verifications/batches")
    ResponseEntity<List<SavedVerificationBatchResponse>> findSavedVerificationBatches(
        @RequestParam @NotNull @Positive Long universityId,
        @RequestParam @Min(2000) @Max(2100) int admissionYear
    );

    @Operation(summary = "저장된 일괄 성적검증 결과 목록 조회")
    @GetMapping("/saved-verifications")
    ResponseEntity<SavedVerificationPageResponse> findSavedVerificationResults(
        @RequestParam @NotNull @Positive Long sourceImportId,
        @RequestParam(required = false) @Size(max = 100) String keyword,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    );

    @Operation(summary = "저장된 일괄 성적검증 상세 조회")
    @GetMapping("/saved-verifications/{verificationRunId}")
    ResponseEntity<SavedVerificationDetailResponse> findSavedVerificationDetail(
        @PathVariable Long verificationRunId
    );

    @Operation(summary = "저장된 일괄 성적검증 결과 Excel 다운로드")
    @GetMapping(
        value = "/saved-verifications/batches/{sourceImportId}/export",
        produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    ResponseEntity<StreamingResponseBody> exportSavedVerificationBatch(@PathVariable Long sourceImportId);

    @Operation(summary = "저장된 성적검증 결과 Excel 생성 시작")
    @PostMapping("/saved-verifications/batches/{sourceImportId}/exports")
    ResponseEntity<SavedVerificationExportStartResponse> startSavedVerificationExport(
        @PathVariable Long sourceImportId
    );

    @Operation(summary = "저장된 성적검증 결과 Excel 생성 상태 조회")
    @GetMapping("/saved-verifications/exports/{exportId}")
    ResponseEntity<SavedVerificationExportStatusResponse> findSavedVerificationExport(
        @PathVariable UUID exportId
    );

    @Operation(summary = "준비된 성적검증 결과 Excel 다운로드")
    @GetMapping(
        value = "/saved-verifications/exports/{exportId}/file",
        produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    ResponseEntity<StreamingResponseBody> downloadSavedVerificationExport(
        @PathVariable UUID exportId
    );

    @Operation(summary = "DB 저장 학생부 성적검증 결과 다운로드")
    @GetMapping(
        value = "/verifications/export",
        produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    ResponseEntity<StreamingResponseBody> exportStoredTranscriptVerification(
        @RequestParam @NotNull @Positive Long universityId,
        @RequestParam @Min(2000) @Max(2100) int admissionYear
    );

    @Operation(summary = "학생부 Excel 업로드 양식 다운로드")
    @GetMapping(value = "/imports/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ResponseEntity<byte[]> downloadTemplate();

    @Operation(summary = "최근 학생부 가져오기 이력 조회")
    @GetMapping("/imports")
    ResponseEntity<List<TranscriptImportSummaryResponse>> findImports(
        @RequestParam @NotNull @Positive Long universityId
    );

    @Operation(summary = "학생부 가져오기 이력 조회")
    @GetMapping("/imports/{importId}")
    ResponseEntity<TranscriptImportSummaryResponse> findImport(@PathVariable Long importId);

    @Operation(summary = "학생부 가져오기 처리 결과 다운로드")
    @GetMapping(
        value = "/imports/{importId}/result",
        produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    ResponseEntity<StreamingResponseBody> downloadImportResult(@PathVariable Long importId);

    @Operation(summary = "지원자 목록 조회", description = "지원번호, 학생명, 고등학교명으로 검색할 수 있습니다.")
    @GetMapping("/students")
    ResponseEntity<StudentPageResponse> findStudents(
        @RequestParam("universityId") @NotNull @Positive Long universityId,
        @RequestParam("admissionYear") @Min(2000) @Max(2100) int admissionYear,
        @RequestParam(value = "keyword", required = false) @Size(max = 100) String keyword,
        @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
        @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size
    );

    @Operation(summary = "지원자 학생부 조회")
    @GetMapping("/students/{applicantNumber}")
    ResponseEntity<StudentTranscriptResponse> findStudentTranscript(
        @PathVariable("applicantNumber") String applicantNumber,
        @RequestParam("universityId") @NotNull @Positive Long universityId,
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
