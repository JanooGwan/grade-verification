package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.TRANSCRIPT_IMPORT_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.TRANSCRIPT_STUDENT_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.TRANSCRIPT_COURSE_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_TRANSCRIPT_COURSE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_STUDENT_COMMON_DATA;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.global.util.TextNormalizer;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.StudentAttendance;
import com.jinhakapply.gradevalidation.transcript.domain.StudentSchoolViolenceAction;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.StudentGedSubjectScore;
import com.jinhakapply.gradevalidation.transcript.domain.StudentLegacyGradeSummary;
import com.jinhakapply.gradevalidation.transcript.domain.LegacySummaryType;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.dto.StudentTranscriptResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentSummaryResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportSummaryResponse;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentCommonDataRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpsertTranscriptCourseRequest;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentAttendanceRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentSchoolViolenceActionRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentCourseSummaryProjection;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentGedSubjectScoreRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentLegacyGradeSummaryRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
@RequiredArgsConstructor
public class TranscriptService {

    private static final long MAX_FILE_SIZE = 40L * 1024 * 1024;

    private final TranscriptExcelParser excelParser;
    private final TransferExcelParser transferExcelParser;
    private final ApplicantSchoolInfoExcelParser applicantSchoolInfoExcelParser;
    private final TransferImportService transferImportService;
    private final TranscriptImportResultExcelWriter importResultExcelWriter;
    private final SyuImportScoreExcelWriter syuImportScoreExcelWriter;
    private final StudentRepository studentRepository;
    private final StudentTranscriptCourseRepository courseRepository;
    private final StudentTranscriptImportRepository importRepository;
    private final StudentAttendanceRepository attendanceRepository;
    private final StudentSchoolViolenceActionRepository schoolViolenceRepository;
    private final StudentGedSubjectScoreRepository gedSubjectScoreRepository;
    private final StudentLegacyGradeSummaryRepository legacyGradeSummaryRepository;
    private final UniversityRepository universityRepository;
    private final TranscriptSnapshotReplacementService snapshotReplacementService;

    @Transactional
    public TranscriptImportResponse importExcel(int admissionYear, MultipartFile file) {
        return importExcel(admissionYear, TranscriptImportMode.VALID_ROWS_ONLY, null, file);
    }

    @Transactional
    public TranscriptImportResponse importExcel(int admissionYear, TranscriptImportMode mode, MultipartFile file) {
        return importExcel(admissionYear, mode, null, file);
    }

    @Transactional
    public TranscriptImportResponse importExcel(
        int admissionYear,
        TranscriptImportMode mode,
        Long universityId,
        MultipartFile file
    ) {
        return importExcel(admissionYear, mode, universityId, file, null);
    }

    @Transactional
    public TranscriptImportResponse importExcel(
        int admissionYear,
        TranscriptImportMode mode,
        Long universityId,
        MultipartFile file,
        MultipartFile schoolInfoFile
    ) {
        validateFile(admissionYear, file);
        University university = requireUniversity(universityId);
        if (transferExcelParser.supports(file)) {
            ApplicantSchoolInfoParseResult schoolInfo = parseSchoolInfoFile(schoolInfoFile);
            return transferImportService.importExcel(
                admissionYear, universityId, mode, file, sha256(file), schoolInfo
            );
        }
        String originalFileName = safeFileName(file.getOriginalFilename());
        String sourceFormat = "STANDARD_TRANSCRIPT_V1";
        TranscriptExcelParseResult parseResult = excelParser.parse(file);
        if (parseResult.totalRows() == 0) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "업로드할 성적 행이 없습니다.");
        }
        if (mode == TranscriptImportMode.ALL_OR_NOTHING && !parseResult.errors().isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "오류 행이 %,d건 있어 전체 저장을 취소했습니다. 미리보기에서 오류를 수정하거나 유효 행만 저장을 선택해 주세요."
                    .formatted(parseResult.errors().size()));
        }

        StudentTranscriptImport previousImport = importRepository
            .findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
                university.getId(), admissionYear, completedImportStatuses()
            ).orElse(null);
        if (previousImport != null && !parseResult.errors().isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "기존 저장본을 지우지 않도록 오류가 있는 파일은 재업로드할 수 없습니다. 오류를 모두 수정한 뒤 다시 저장해 주세요.");
        }
        TranscriptSnapshotReplacementService.SnapshotScope snapshot = snapshotReplacementService.clear(
            university.getId(), admissionYear, false
        );
        StudentTranscriptImport transcriptImport = importRepository.save(StudentTranscriptImport.create(
            university,
            admissionYear,
            originalFileName,
            mode,
            sha256(file),
            parseResult.totalRows(),
            parseResult.rows().size(),
            parseResult.errors().size(),
            sourceFormat
        ));

        Map<String, TranscriptExcelRow> firstRowByApplicant = parseResult.rows().stream().collect(Collectors.toMap(
            TranscriptExcelRow::applicantNumber,
            Function.identity(),
            (first, ignored) -> first
        ));
        Map<String, Student> studentCache = studentRepository.findAllByUniversity_IdAndAdmissionYearAndApplicantNumberIn(
            university.getId(), admissionYear,
            firstRowByApplicant.keySet()
        ).stream().collect(Collectors.toMap(Student::getApplicantNumber, Function.identity()));
        Set<String> createdApplicants = new HashSet<>();
        Set<String> updatedApplicants = new HashSet<>(studentCache.keySet());
        firstRowByApplicant.forEach((applicantNumber, row) -> {
            if (!studentCache.containsKey(applicantNumber)) {
                Student created = studentRepository.save(Student.create(
                    university,
                    admissionYear,
                    applicantNumber,
                    row.studentName(),
                    row.highSchoolCode(),
                    row.highSchoolName(),
                    row.graduationYear()
                ));
                studentCache.put(applicantNumber, created);
                createdApplicants.add(applicantNumber);
            }
        });
        studentCache.values().forEach(student -> TransferImportService.applySchoolInfo(student, null));
        int createdCourses = 0;

        for (TranscriptExcelRow row : parseResult.rows()) {
            Student student = studentCache.get(row.applicantNumber());
            student.updateProfile(
                row.studentName(),
                row.highSchoolCode(),
                row.highSchoolName(),
                row.graduationYear()
            );
            StudentTranscriptCourse course = StudentTranscriptCourse.create(
                student,
                row.schoolYear(),
                row.semester(),
                row.subjectCategory(),
                row.courseName()
            );
            course.attachSourceImport(transcriptImport);

            course.updateScore(
                row.grade(),
                row.gradeScale(),
                row.achievement(),
                row.rawScore(),
                row.meanScore(),
                row.standardDeviation(),
                row.studentCount(),
                row.rankPosition(),
                row.tiedRankCount(),
                row.legacyAchievement(),
                row.credits(),
                row.careerSubject(),
                row.professionalCourse(),
                originalFileName,
                row.rowNumber()
            );
            courseRepository.save(course);
            createdCourses++;
        }
        snapshotReplacementService.deleteMissingStudents(
            snapshot.existingStudents(), firstRowByApplicant.keySet()
        );

        return new TranscriptImportResponse(
            transcriptImport.getId(),
            transcriptImport.getStatus(),
            sourceFormat,
            transcriptImport.getTotalRows(),
            transcriptImport.getImportedRows(),
            transcriptImport.getFailedRows(),
            parseResult.skipped().size(),
            createdApplicants.size(),
            updatedApplicants.size(),
            createdCourses,
            0,
            snapshot.deletedCourses(),
            0,
            0,
            snapshot.deletedApplications(),
            0,
            0,
            parseResult.errors(),
            List.of()
        );
    }

    @Transactional(readOnly = true)
    public List<TranscriptImportSummaryResponse> findImports(Long universityId) {
        return importRepository.findTop50ByUniversity_IdOrderByCreatedAtDesc(requireUniversity(universityId).getId()).stream()
            .map(TranscriptImportSummaryResponse::from)
            .toList();
    }

    private List<TranscriptImportStatus> completedImportStatuses() {
        return List.of(TranscriptImportStatus.COMPLETED, TranscriptImportStatus.COMPLETED_WITH_ERRORS);
    }

    public byte[] createExcelTemplate() {
        String[] headers = {
            "지원자번호", "학생명", "고교코드", "고교명", "졸업연도", "학년", "학기", "교과",
            "과목명", "석차등급", "등급제", "성취도", "원점수", "과목평균", "표준편차", "수강자수",
            "석차", "동석차인원", "구교육과정평어",
            "이수단위", "진로선택", "전문교과"
        };
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("학생부 성적");
            var row = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                row.createCell(index).setCellValue(headers[index]);
                sheet.setColumnWidth(index, Math.max(12, headers[index].length() + 4) * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Excel 양식을 생성하지 못했습니다.", exception);
        }
    }

    @Transactional(readOnly = true)
    public TranscriptImportSummaryResponse findImport(Long importId) {
        return importRepository.findById(importId)
            .map(TranscriptImportSummaryResponse::from)
            .orElseThrow(() -> CustomException.of(TRANSCRIPT_IMPORT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public byte[] exportImportResult(Long importId) {
        StudentTranscriptImport transcriptImport = importRepository.findById(importId)
            .orElseThrow(() -> CustomException.of(TRANSCRIPT_IMPORT_NOT_FOUND));
        if (transcriptImport.getStatus() != TranscriptImportStatus.COMPLETED
            && transcriptImport.getStatus() != TranscriptImportStatus.COMPLETED_WITH_ERRORS) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "완료된 가져오기 작업만 결과를 다운로드할 수 있습니다.");
        }
        return importResultExcelWriter.write(transcriptImport);
    }

    @Transactional(readOnly = true)
    public void writeImportResult(Long importId, OutputStream output) {
        StudentTranscriptImport transcriptImport = importRepository.findById(importId)
            .orElseThrow(() -> CustomException.of(TRANSCRIPT_IMPORT_NOT_FOUND));
        if (transcriptImport.getStatus() != TranscriptImportStatus.COMPLETED
            && transcriptImport.getStatus() != TranscriptImportStatus.COMPLETED_WITH_ERRORS) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "완료된 가져오기 작업만 결과를 다운로드할 수 있습니다.");
        }
        if ("SYU_SOURCE_WORKBOOK_V1".equals(transcriptImport.getSourceFormat())) {
            syuImportScoreExcelWriter.write(transcriptImport, output);
            return;
        }
        try {
            output.write(importResultExcelWriter.write(transcriptImport));
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "가져오기 처리 결과를 전송하지 못했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public StudentPageResponse findStudents(
        Long universityId,
        int admissionYear,
        String keyword,
        int page,
        int size
    ) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Student> students = studentRepository.search(
            requireUniversity(universityId).getId(), admissionYear,
            normalizedKeyword,
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "applicantNumber"))
        );
        List<Long> studentIds = students.getContent().stream().map(Student::getId).toList();
        Map<Long, CourseStats> courseStats = new HashMap<>();
        if (!studentIds.isEmpty()) {
            courseRepository.summarizeByStudentIds(studentIds).forEach(summary -> courseStats.put(
                summary.getStudentId(),
                new CourseStats(summary.getCourseCount(), averageGrade(summary))
            ));
        }
        List<StudentSummaryResponse> content = students.getContent().stream().map(student -> {
            CourseStats stats = courseStats.getOrDefault(student.getId(), CourseStats.EMPTY);
            return StudentSummaryResponse.of(student, stats.courseCount(), stats.averageGrade());
        }).toList();
        return new StudentPageResponse(
            content,
            students.getNumber(),
            students.getSize(),
            students.getTotalElements(),
            students.getTotalPages(),
            students.isFirst(),
            students.isLast()
        );
    }

    @Transactional(readOnly = true)
    public StudentTranscriptResponse findStudentTranscript(
        Long universityId, int admissionYear, String applicantNumber
    ) {
        Student student = studentRepository.findByUniversity_IdAndAdmissionYearAndApplicantNumber(
            requireUniversity(universityId).getId(), admissionYear, applicantNumber
        )
            .orElseThrow(() -> CustomException.of(TRANSCRIPT_STUDENT_NOT_FOUND));
        return StudentTranscriptResponse.of(
            student,
            gedSubjectScoreRepository.findAllByStudent_IdOrderBySubjectTypeAscSubjectNameAsc(student.getId()),
            legacyGradeSummaryRepository.findAllByStudent_IdOrderBySchoolYearAscSemesterAsc(student.getId()),
            attendanceRepository.findAllByStudent_IdOrderBySchoolYearAsc(student.getId()),
            schoolViolenceRepository.findAllByStudent_IdOrderBySchoolYearAscActionNumberAsc(student.getId()),
            courseRepository.findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(student.getId())
        );
    }

    @Transactional
    public StudentTranscriptResponse updateStudent(Long studentId, UpdateStudentRequest request) {
        Student student = findStudent(studentId);
        student.updateProfile(request.name().trim(), clean(request.highSchoolCode()), clean(request.highSchoolName()),
            request.graduationYear());
        return transcript(student);
    }

    @Transactional
    public StudentTranscriptResponse updateStudentCommonData(Long studentId, UpdateStudentCommonDataRequest request) {
        Student student = findStudent(studentId);
        validateCommonData(request);
        student.updateCommonEvaluationProfile(
            request.educationBackground(), request.highSchoolType(), request.graduationStatus(), request.gedAverageScore()
        );

        attendanceRepository.deleteAllByStudent_Id(studentId);
        attendanceRepository.flush();
        attendanceRepository.saveAll(request.attendance().stream().map(item -> {
            StudentAttendance attendance = StudentAttendance.create(student, item.schoolYear());
            attendance.update(item.unexcusedAbsenceDays(), item.unexcusedTardyCount(),
                item.unexcusedEarlyLeaveCount(), item.unexcusedClassAbsenceCount());
            return attendance;
        }).toList());

        schoolViolenceRepository.deleteAllByStudent_Id(studentId);
        schoolViolenceRepository.flush();
        schoolViolenceRepository.saveAll(request.schoolViolenceActions().stream().map(item ->
            StudentSchoolViolenceAction.create(
                student, item.schoolYear(), item.actionNumber(), item.actionDate(), item.active(), clean(item.note())
            )
        ).toList());

        gedSubjectScoreRepository.deleteAllByStudent_Id(studentId);
        gedSubjectScoreRepository.flush();
        gedSubjectScoreRepository.saveAll(request.gedSubjectScores().stream().map(item ->
            StudentGedSubjectScore.create(student, item.subjectType(), item.subjectName(), item.score())
        ).toList());

        legacyGradeSummaryRepository.deleteAllByStudent_Id(studentId);
        legacyGradeSummaryRepository.flush();
        legacyGradeSummaryRepository.saveAll(request.legacyGradeSummaries().stream().map(item ->
            StudentLegacyGradeSummary.create(student, item.summaryType(), item.schoolYear(), item.semester(),
                item.rankPosition(), item.tiedRankCount(), item.cohortSize(), item.credits())
        ).toList());
        return transcript(student);
    }

    @Transactional
    public void deleteStudent(Long studentId) {
        studentRepository.delete(findStudent(studentId));
    }

    @Transactional
    public StudentTranscriptResponse.CourseResponse createCourse(
        Long studentId,
        UpsertTranscriptCourseRequest request
    ) {
        Student student = findStudent(studentId);
        validateCourse(request);
        requireUniqueCourse(studentId, null, request);
        StudentTranscriptCourse course = StudentTranscriptCourse.create(
            student, request.schoolYear(), request.semester(), request.subjectCategory(), request.courseName().trim()
        );
        updateCourse(course, request);
        courseRepository.save(course);
        return StudentTranscriptResponse.CourseResponse.from(course);
    }

    @Transactional
    public StudentTranscriptResponse.CourseResponse updateCourse(
        Long studentId,
        Long courseId,
        UpsertTranscriptCourseRequest request
    ) {
        validateCourse(request);
        StudentTranscriptCourse course = findCourse(studentId, courseId);
        requireUniqueCourse(studentId, courseId, request);
        updateCourse(course, request);
        return StudentTranscriptResponse.CourseResponse.from(course);
    }

    @Transactional
    public void deleteCourse(Long studentId, Long courseId) {
        courseRepository.delete(findCourse(studentId, courseId));
    }

    private StudentTranscriptResponse transcript(Student student) {
        return StudentTranscriptResponse.of(student,
            gedSubjectScoreRepository.findAllByStudent_IdOrderBySubjectTypeAscSubjectNameAsc(student.getId()),
            legacyGradeSummaryRepository.findAllByStudent_IdOrderBySchoolYearAscSemesterAsc(student.getId()),
            attendanceRepository.findAllByStudent_IdOrderBySchoolYearAsc(student.getId()),
            schoolViolenceRepository.findAllByStudent_IdOrderBySchoolYearAscActionNumberAsc(student.getId()),
            courseRepository.findAllByStudent_IdOrderBySchoolYearAscSemesterAscCourseNameAsc(student.getId()));
    }

    private void validateCommonData(UpdateStudentCommonDataRequest request) {
        Set<Integer> years = new HashSet<>();
        if (request.attendance().stream().anyMatch(item -> !years.add(item.schoolYear()))) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA, "출결은 학년별로 한 건만 등록할 수 있습니다.");
        }
        if (request.educationBackground() == EducationBackground.GED
            && request.gedAverageScore() == null && request.gedSubjectScores().isEmpty()) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA,
                "검정고시 출신자는 전 과목 평균점수 또는 과목별 점수가 필요합니다.");
        }
        if (request.educationBackground() != EducationBackground.GED
            && (request.gedAverageScore() != null || !request.gedSubjectScores().isEmpty())) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA,
                "검정고시가 아닌 지원자에게 검정고시 점수를 입력할 수 없습니다.");
        }
        if (request.educationBackground() != EducationBackground.DOMESTIC_HIGH_SCHOOL
            && request.highSchoolType() != HighSchoolType.GENERAL) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA,
                "고교 유형은 국내 고등학교 지원자에게만 설정할 수 있습니다.");
        }
        if (request.educationBackground() != EducationBackground.DOMESTIC_HIGH_SCHOOL
            && !request.legacyGradeSummaries().isEmpty()) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA,
                "구교육과정 학기·학년 석차는 국내 고등학교 지원자에게만 입력할 수 있습니다.");
        }
        Set<String> gedNames = new HashSet<>();
        if (request.gedSubjectScores().stream().anyMatch(item -> !gedNames.add(item.subjectName().trim()))) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA, "검정고시 과목명은 중복될 수 없습니다.");
        }
        Set<String> summaries = new HashSet<>();
        for (UpdateStudentCommonDataRequest.LegacyGradeSummary item : request.legacyGradeSummaries()) {
            if (item.summaryType() == LegacySummaryType.SEMESTER && item.semester() == null) {
                throw CustomException.of(INVALID_STUDENT_COMMON_DATA, "학기 석차 요약에는 학기가 필요합니다.");
            }
            if (item.summaryType() == LegacySummaryType.YEAR && item.semester() != null) {
                throw CustomException.of(INVALID_STUDENT_COMMON_DATA, "학년 석차 요약에는 학기를 입력할 수 없습니다.");
            }
            if (item.rankPosition() > item.cohortSize()
                || (item.tiedRankCount() != null
                && item.rankPosition() + item.tiedRankCount() - 1 > item.cohortSize())) {
                throw CustomException.of(INVALID_STUDENT_COMMON_DATA, "구교육과정 석차·동석차 범위가 재적수를 초과합니다.");
            }
            String key = item.summaryType() + ":" + item.schoolYear() + ":" + item.semester();
            if (!summaries.add(key)) {
                throw CustomException.of(INVALID_STUDENT_COMMON_DATA, "같은 학기 또는 학년의 석차 요약이 중복되었습니다.");
            }
        }
    }

    private void updateCourse(StudentTranscriptCourse course, UpsertTranscriptCourseRequest request) {
        course.updateCourse(request.schoolYear(), request.semester(), request.subjectCategory(), request.courseName(),
            request.grade(), request.gradeScale(), request.achievement(), request.rawScore(), request.meanScore(),
            request.standardDeviation(), request.studentCount(), request.rankPosition(), request.tiedRankCount(),
            request.legacyAchievement(), request.credits(), request.careerSubject(), request.professionalCourse(),
            "MANUAL", 0);
    }

    private void validateCourse(UpsertTranscriptCourseRequest request) {
        if (request.grade() == null && request.achievement() == null && request.rankPosition() == null
            && request.legacyAchievement() == null) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "석차등급, 성취도, 석차 또는 구교육과정 평어 중 하나는 필요합니다.");
        }
        if (request.grade() != null && request.grade() > request.gradeScale().maximumGrade()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                request.gradeScale() + " 등급제에서는 " + request.gradeScale().maximumGrade() + "등급까지만 입력할 수 있습니다.");
        }
        if (request.rankPosition() != null && request.studentCount() == null) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "석차를 입력할 때는 재적수도 필요합니다.");
        }
        if (request.rankPosition() != null && request.rankPosition() > request.studentCount()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "석차는 재적수 이하여야 합니다.");
        }
        if (request.tiedRankCount() != null && request.rankPosition() == null) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "동석차 인원을 입력할 때는 석차도 필요합니다.");
        }
    }

    private void requireUniqueCourse(Long studentId, Long currentCourseId, UpsertTranscriptCourseRequest request) {
        courseRepository.findByStudent_IdAndSchoolYearAndSemesterAndCourseNameNormalized(
            studentId, request.schoolYear(), request.semester(), TextNormalizer.normalizeCourseName(request.courseName())
        ).filter(existing -> !existing.getId().equals(currentCourseId))
            .ifPresent(existing -> { throw CustomException.of(DUPLICATE_TRANSCRIPT_COURSE); });
    }

    private Student findStudent(Long studentId) {
        return studentRepository.findById(studentId)
            .orElseThrow(() -> CustomException.of(TRANSCRIPT_STUDENT_NOT_FOUND));
    }

    private University requireUniversity(Long universityId) {
        if (universityId == null || universityId <= 0) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "대학을 선택해 주세요.");
        }
        return universityRepository.findById(universityId)
            .orElseThrow(() -> CustomException.of(com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND));
    }

    private StudentTranscriptCourse findCourse(Long studentId, Long courseId) {
        return courseRepository.findByIdAndStudent_Id(courseId, studentId)
            .orElseThrow(() -> CustomException.of(TRANSCRIPT_COURSE_NOT_FOUND));
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal averageGrade(StudentCourseSummaryProjection summary) {
        return summary.getAverageGrade() == null ? null
            : BigDecimal.valueOf(summary.getAverageGrade()).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateFile(int admissionYear, MultipartFile file) {
        if (admissionYear < 2000 || admissionYear > 2100) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "모집연도는 2000~2100 사이여야 합니다.");
        }
        if (file == null || file.isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "업로드 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "파일 크기는 40MB를 초과할 수 없습니다.");
        }
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("")
            .toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "지원하는 확장자는 .xlsx, .xls입니다.");
        }
    }

    private ApplicantSchoolInfoParseResult parseSchoolInfoFile(MultipartFile schoolInfoFile) {
        if (schoolInfoFile == null || schoolInfoFile.isEmpty()) return ApplicantSchoolInfoParseResult.empty();
        if (schoolInfoFile.getSize() > MAX_FILE_SIZE) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "지원자 추가정보 Excel 파일은 40MB 이하여야 합니다.");
        }
        String fileName = schoolInfoFile.getOriginalFilename();
        if (fileName == null || (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")
            && !fileName.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "지원자 추가정보는 Excel 파일(.xlsx, .xls)만 가능합니다.");
        }
        return applicantSchoolInfoExcelParser.parse(schoolInfoFile);
    }

    private String safeFileName(String originalFileName) {
        String cleaned = StringUtils.cleanPath(Optional.ofNullable(originalFileName).orElse("upload.xlsx"))
            .replace('\\', '/');
        String fileName = cleaned.substring(cleaned.lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            return "upload.xlsx";
        }
        return fileName.length() > 255 ? fileName.substring(fileName.length() - 255) : fileName;
    }

    private String sha256(MultipartFile file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "파일 해시를 계산하지 못했습니다.");
        }
    }

    private record CourseStats(long courseCount, BigDecimal averageGrade) {
        private static final CourseStats EMPTY = new CourseStats(0, null);
    }
}
