package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.TRANSCRIPT_IMPORT_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.TRANSCRIPT_STUDENT_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.TRANSCRIPT_COURSE_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_TRANSCRIPT_COURSE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_STUDENT_COMMON_DATA;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.StudentAttendance;
import com.jinhakapply.gradevalidation.transcript.domain.StudentSchoolViolenceAction;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import com.jinhakapply.gradevalidation.transcript.dto.StudentTranscriptResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentPageResponse;
import com.jinhakapply.gradevalidation.transcript.dto.StudentSummaryResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportSummaryResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpdateStudentCommonDataRequest;
import com.jinhakapply.gradevalidation.transcript.dto.UpsertTranscriptCourseRequest;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentAttendanceRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentSchoolViolenceActionRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentCourseSummaryProjection;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
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

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final TranscriptExcelParser excelParser;
    private final StudentRepository studentRepository;
    private final StudentTranscriptCourseRepository courseRepository;
    private final StudentTranscriptImportRepository importRepository;
    private final StudentAttendanceRepository attendanceRepository;
    private final StudentSchoolViolenceActionRepository schoolViolenceRepository;

    @Transactional
    public TranscriptImportResponse importExcel(int admissionYear, MultipartFile file) {
        return importExcel(admissionYear, TranscriptImportMode.VALID_ROWS_ONLY, file);
    }

    @Transactional
    public TranscriptImportResponse importExcel(int admissionYear, TranscriptImportMode mode, MultipartFile file) {
        validateFile(admissionYear, file);
        String originalFileName = safeFileName(file.getOriginalFilename());
        TranscriptExcelParseResult parseResult = excelParser.parse(file);
        if (parseResult.totalRows() == 0) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "업로드할 성적 행이 없습니다.");
        }
        if (mode == TranscriptImportMode.ALL_OR_NOTHING && !parseResult.errors().isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "오류 행이 %,d건 있어 전체 저장을 취소했습니다. 미리보기에서 오류를 수정하거나 유효 행만 저장을 선택해 주세요."
                    .formatted(parseResult.errors().size()));
        }

        Map<String, TranscriptExcelRow> firstRowByApplicant = parseResult.rows().stream().collect(Collectors.toMap(
            TranscriptExcelRow::applicantNumber,
            Function.identity(),
            (first, ignored) -> first
        ));
        Map<String, Student> studentCache = studentRepository.findAllByAdmissionYearAndApplicantNumberIn(
            admissionYear,
            firstRowByApplicant.keySet()
        ).stream().collect(Collectors.toMap(Student::getApplicantNumber, Function.identity()));
        Set<String> createdApplicants = new HashSet<>();
        Set<String> updatedApplicants = new HashSet<>(studentCache.keySet());
        firstRowByApplicant.forEach((applicantNumber, row) -> {
            if (!studentCache.containsKey(applicantNumber)) {
                Student created = studentRepository.save(Student.create(
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
        List<Long> studentIds = studentCache.values().stream().map(Student::getId).toList();
        Map<CourseKey, StudentTranscriptCourse> courseCache = studentIds.isEmpty()
            ? new HashMap<>()
            : courseRepository.findAllByStudent_IdIn(studentIds).stream().collect(Collectors.toMap(
                CourseKey::from,
                Function.identity()
            ));
        int createdCourses = 0;
        int updatedCourses = 0;

        for (TranscriptExcelRow row : parseResult.rows()) {
            Student student = studentCache.get(row.applicantNumber());
            student.updateProfile(
                row.studentName(),
                row.highSchoolCode(),
                row.highSchoolName(),
                row.graduationYear()
            );

            CourseKey courseKey = CourseKey.from(row);
            StudentTranscriptCourse course = courseCache.get(courseKey);
            boolean created = false;
            if (course == null) {
                course = StudentTranscriptCourse.create(
                    student,
                    row.schoolYear(),
                    row.semester(),
                    row.subjectCategory(),
                    row.courseName()
                );
                created = true;
                courseCache.put(courseKey, course);
            }

            course.updateScore(
                row.grade(),
                row.achievement(),
                row.rawScore(),
                row.meanScore(),
                row.standardDeviation(),
                row.studentCount(),
                row.credits(),
                row.careerSubject(),
                row.professionalCourse(),
                originalFileName,
                row.rowNumber()
            );
            if (created) {
                courseRepository.save(course);
                createdCourses++;
            } else {
                updatedCourses++;
            }
        }

        StudentTranscriptImport transcriptImport = importRepository.save(StudentTranscriptImport.create(
            admissionYear,
            originalFileName,
            mode,
            sha256(file),
            parseResult.totalRows(),
            parseResult.rows().size(),
            parseResult.errors().size()
        ));

        return new TranscriptImportResponse(
            transcriptImport.getId(),
            transcriptImport.getStatus(),
            transcriptImport.getTotalRows(),
            transcriptImport.getImportedRows(),
            transcriptImport.getFailedRows(),
            createdApplicants.size(),
            updatedApplicants.size(),
            createdCourses,
            updatedCourses,
            parseResult.errors()
        );
    }

    @Transactional(readOnly = true)
    public TranscriptPreviewResponse previewExcel(int admissionYear, MultipartFile file) {
        validateFile(admissionYear, file);
        TranscriptExcelParseResult result = excelParser.parse(file);
        return new TranscriptPreviewResponse(
            safeFileName(file.getOriginalFilename()),
            sha256(file),
            result.totalRows(),
            result.rows().size(),
            result.errors().size(),
            result.rows().stream().limit(50).map(row -> new TranscriptPreviewResponse.PreviewRow(
                row.rowNumber(), row.applicantNumber(), row.studentName(), row.schoolYear(), row.semester(),
                row.subjectCategory(), row.courseName(), row.grade(), row.achievement(), row.credits()
            )).toList(),
            result.errors()
        );
    }

    @Transactional(readOnly = true)
    public List<TranscriptImportSummaryResponse> findImports() {
        return importRepository.findTop50ByOrderByCreatedAtDesc().stream()
            .map(TranscriptImportSummaryResponse::from)
            .toList();
    }

    public byte[] createExcelTemplate() {
        String[] headers = {
            "지원자번호", "학생명", "고교코드", "고교명", "졸업연도", "학년", "학기", "교과",
            "과목명", "석차등급", "성취도", "원점수", "과목평균", "표준편차", "수강자수",
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
    public StudentPageResponse findStudents(
        int admissionYear,
        String keyword,
        int page,
        int size
    ) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Student> students = studentRepository.search(
            admissionYear,
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
    public StudentTranscriptResponse findStudentTranscript(int admissionYear, String applicantNumber) {
        Student student = studentRepository.findByAdmissionYearAndApplicantNumber(admissionYear, applicantNumber)
            .orElseThrow(() -> CustomException.of(TRANSCRIPT_STUDENT_NOT_FOUND));
        return StudentTranscriptResponse.of(
            student,
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
            && request.gedAverageScore() == null) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA, "검정고시 출신자는 전 과목 평균점수가 필요합니다.");
        }
        if (request.educationBackground() != EducationBackground.GED
            && request.gedAverageScore() != null) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA, "검정고시가 아닌 지원자에게 검정고시 평균점수를 입력할 수 없습니다.");
        }
        if (request.educationBackground() != EducationBackground.DOMESTIC_HIGH_SCHOOL
            && request.highSchoolType() != HighSchoolType.GENERAL) {
            throw CustomException.of(INVALID_STUDENT_COMMON_DATA,
                "고교 유형은 국내 고등학교 지원자에게만 설정할 수 있습니다.");
        }
    }

    private void updateCourse(StudentTranscriptCourse course, UpsertTranscriptCourseRequest request) {
        course.updateCourse(request.schoolYear(), request.semester(), request.subjectCategory(), request.courseName(),
            request.grade(), request.achievement(), request.rawScore(), request.meanScore(), request.standardDeviation(),
            request.studentCount(), request.credits(), request.careerSubject(), request.professionalCourse(),
            "MANUAL", 0);
    }

    private void validateCourse(UpsertTranscriptCourseRequest request) {
        if (request.grade() == null && request.achievement() == null) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "석차등급 또는 성취도 중 하나는 필요합니다.");
        }
    }

    private void requireUniqueCourse(Long studentId, Long currentCourseId, UpsertTranscriptCourseRequest request) {
        courseRepository.findByStudent_IdAndSchoolYearAndSemesterAndSubjectCategoryAndCourseName(
            studentId, request.schoolYear(), request.semester(), request.subjectCategory(), request.courseName().trim()
        ).filter(existing -> !existing.getId().equals(currentCourseId))
            .ifPresent(existing -> { throw CustomException.of(DUPLICATE_TRANSCRIPT_COURSE); });
    }

    private Student findStudent(Long studentId) {
        return studentRepository.findById(studentId)
            .orElseThrow(() -> CustomException.of(TRANSCRIPT_STUDENT_NOT_FOUND));
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
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "파일 크기는 10MB를 초과할 수 없습니다.");
        }
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("")
            .toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "지원하는 확장자는 .xlsx, .xls입니다.");
        }
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

    private record CourseKey(
        String applicantNumber,
        int schoolYear,
        int semester,
        String subjectCategory,
        String courseName
    ) {
        private static CourseKey from(TranscriptExcelRow row) {
            return new CourseKey(
                row.applicantNumber(),
                row.schoolYear(),
                row.semester(),
                row.subjectCategory().name(),
                row.courseName()
            );
        }

        private static CourseKey from(StudentTranscriptCourse course) {
            return new CourseKey(
                course.getStudent().getApplicantNumber(),
                course.getSchoolYear(),
                course.getSemester(),
                course.getSubjectCategory().name(),
                course.getCourseName()
            );
        }
    }

    private record CourseStats(long courseCount, BigDecimal averageGrade) {
        private static final CourseStats EMPTY = new CourseStats(0, null);
    }
}
