package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import com.jinhakapply.gradevalidation.admission.repository.AdmissionTrackRepository;
import com.jinhakapply.gradevalidation.admission.repository.RecruitmentUnitRepository;
import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.global.util.TextNormalizer;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.HighSchoolType;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
class TransferImportService {

    private static final int BATCH_SIZE = 1_000;
    private static final String KBU_UNIVERSITY_CODE = "KBOK";

    private final TransferExcelParser parser;
    private final UniversityRepository universityRepository;
    private final AdmissionTrackRepository admissionTrackRepository;
    private final RecruitmentUnitRepository recruitmentUnitRepository;
    private final StudentApplicationRepository studentApplicationRepository;
    private final StudentRepository studentRepository;
    private final StudentTranscriptCourseRepository courseRepository;
    private final StudentTranscriptImportRepository importRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TranscriptSnapshotReplacementService snapshotReplacementService;

    @Transactional
    TranscriptImportResponse importExcel(
        int admissionYear,
        Long universityId,
        TranscriptImportMode mode,
        MultipartFile file,
        String fileSha256
    ) {
        return importExcel(
            admissionYear, universityId, mode, file, fileSha256, ApplicantSchoolInfoParseResult.empty()
        );
    }

    @Transactional
    TranscriptImportResponse importExcel(
        int admissionYear,
        Long universityId,
        TranscriptImportMode mode,
        MultipartFile file,
        String fileSha256,
        ApplicantSchoolInfoParseResult schoolInfoResult
    ) {
        if (universityId == null) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "전달양식을 가져올 대상 대학교를 선택해 주세요.");
        }
        University university = universityRepository.findById(universityId)
            .orElseThrow(() -> CustomException.of(UNIVERSITY_NOT_FOUND));
        TransferExcelParseResult result = parser.parse(file);
        boolean mismatchedYear = result.applications().stream()
            .anyMatch(row -> row.admissionYear() != admissionYear);
        if (mismatchedYear) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "화면의 모집연도와 전달양식의 입학연도가 일치하지 않습니다.");
        }
        boolean mismatchedSchoolInfoYear = schoolInfoResult.rows().stream()
            .anyMatch(row -> row.admissionYear() != null && row.admissionYear() != admissionYear);
        if (mismatchedSchoolInfoYear) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "DB 저장 시에는 화면의 모집연도와 지원자 추가정보의 입학연도가 일치해야 합니다.");
        }
        if (mode == TranscriptImportMode.ALL_OR_NOTHING && result.invalidRows() > 0) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "오류 행이 %,d건 있어 전체 저장을 취소했습니다.".formatted(result.invalidRows()));
        }
        university = universityRepository.findByIdForUpdate(universityId)
            .orElseThrow(() -> CustomException.of(UNIVERSITY_NOT_FOUND));
        StudentTranscriptImport previousImport = importRepository
            .findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
                university.getId(), admissionYear,
                List.of(TranscriptImportStatus.COMPLETED, TranscriptImportStatus.COMPLETED_WITH_ERRORS)
            ).orElse(null);
        if (previousImport != null && result.invalidRows() > 0) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "기존 저장본을 지우지 않도록 오류가 있는 파일은 재업로드할 수 없습니다. 오류를 모두 수정한 뒤 다시 저장해 주세요.");
        }

        TranscriptSnapshotReplacementService.SnapshotScope snapshot = snapshotReplacementService.clear(
            university.getId(), admissionYear, false
        );

        Set<String> applicantNumbers = result.courses().stream()
            .map(TranscriptExcelRow::applicantNumber)
            .collect(Collectors.toCollection(HashSet::new));
        applicantNumbers.addAll(result.applications().stream()
            .map(TransferApplicationRow::applicantNumber).toList());
        Map<String, TransferApplicationRow> applicationByApplicant = result.applications().stream()
            .collect(Collectors.toMap(TransferApplicationRow::applicantNumber, Function.identity(), (first, ignored) -> first));
        Map<String, ApplicantSchoolInfoRow> schoolInfoByApplicant = schoolInfoResult.byApplicantNumber();

        Map<String, Student> students = studentRepository.findAllByUniversity_IdAndAdmissionYearAndApplicantNumberIn(
            university.getId(), admissionYear, applicantNumbers
        ).stream().collect(Collectors.toMap(Student::getApplicantNumber, Function.identity()));
        int updatedStudents = students.size();
        int createdStudents = 0;
        for (String applicantNumber : applicantNumbers) {
            Student existing = students.get(applicantNumber);
            TransferApplicationRow application = applicationByApplicant.get(applicantNumber);
            ApplicantSchoolInfoRow schoolInfo = schoolInfoByApplicant.get(applicantNumber);
            Integer graduationYear = schoolInfo != null && schoolInfo.graduationYear() != null
                ? schoolInfo.graduationYear() : application == null ? null : application.graduationYear();
            String highSchoolCode = schoolInfo == null ? null : schoolInfo.highSchoolCode();
            String highSchoolName = schoolInfo == null ? null : schoolInfo.highSchoolName();
            if (existing == null) {
                Student created = studentRepository.save(Student.create(
                    university,
                    admissionYear,
                    applicantNumber,
                    "미등록",
                    highSchoolCode,
                    highSchoolName,
                    graduationYear
                ));
                applySchoolInfo(created, schoolInfo);
                students.put(applicantNumber, created);
                createdStudents++;
            } else {
                existing.updateProfile(
                    existing.getName(),
                    highSchoolCode,
                    highSchoolName,
                    graduationYear
                );
                applySchoolInfo(existing, schoolInfo);
            }
        }

        String sourceFileName = safeFileName(file.getOriginalFilename());
        StudentTranscriptImport transcriptImport = importRepository.save(StudentTranscriptImport.create(
            university,
            admissionYear,
            sourceFileName,
            mode,
            fileSha256,
            result.totalRows(),
            result.courses().size(),
            result.invalidRows(),
            result.sourceFormat()
        ));
        CatalogResult catalog = importApplications(
            university,
            admissionYear,
            result.applications(),
            students,
            transcriptImport,
            snapshot.deletedApplications()
        );
        CourseResult courses = replaceCourses(
            result.courses(), students, sourceFileName, transcriptImport, snapshot.deletedCourses()
        );
        snapshotReplacementService.deleteMissingStudents(snapshot.existingStudents(), applicantNumbers);
        List<String> warnings = new ArrayList<>(result.warnings());
        warnings.add(schoolInfoImportWarning(result.applications(), schoolInfoResult));
        return new TranscriptImportResponse(
            transcriptImport.getId(), transcriptImport.getStatus(), result.sourceFormat(),
            result.totalRows(), result.courses().size(), result.invalidRows(),
            result.skippedRows(),
            createdStudents, updatedStudents, courses.created(), courses.updated(), courses.deleted(),
            result.applications().size(), catalog.createdApplications(), catalog.deletedApplications(), catalog.createdTracks(),
            catalog.createdUnits(), result.errors(), List.copyOf(warnings)
        );
    }

    private String schoolInfoImportWarning(
        List<TransferApplicationRow> applications,
        ApplicantSchoolInfoParseResult schoolInfo
    ) {
        Set<String> applicationNumbers = applications.stream()
            .map(TransferApplicationRow::applicantNumber)
            .collect(Collectors.toSet());
        long linked = schoolInfo.rows().stream()
            .filter(row -> applicationNumbers.contains(row.applicantNumber()))
            .count();
        long allCourseTypes = schoolInfo.rows().stream()
            .filter(row -> applicationNumbers.contains(row.applicantNumber()))
            .filter(row -> row.highSchoolType().usesHanshinAllOrdinaryCoursesPolicy())
            .count();
        long missing = applicationNumbers.stream()
            .filter(applicantNumber -> !schoolInfo.byApplicantNumber().containsKey(applicantNumber))
            .count();
        return "지원자 추가정보 %,d건을 저장했습니다. 전 과목 반영 고교유형 %,d건, 미연결 지원자 %,d건입니다."
            .formatted(linked, allCourseTypes, missing);
    }

    static void applySchoolInfo(Student student, ApplicantSchoolInfoRow schoolInfo) {
        if (schoolInfo == null) {
            student.updateCommonEvaluationProfile(
                EducationBackground.DOMESTIC_HIGH_SCHOOL,
                HighSchoolType.GENERAL,
                student.getGraduationStatus(),
                null
            );
            student.updateApplicantHighSchoolCategoryCode(null);
            return;
        }
        student.updateCommonEvaluationProfile(
            schoolInfo.educationBackground(),
            schoolInfo.highSchoolType(),
            student.getGraduationStatus(),
            student.getGedAverageScore()
        );
        student.updateApplicantHighSchoolCategoryCode(schoolInfo.applicantHighSchoolCategoryCode());
    }

    CatalogResult importApplications(
        University university,
        int admissionYear,
        List<TransferApplicationRow> rows,
        Map<String, Student> students,
        StudentTranscriptImport transcriptImport,
        int deletedApplications
    ) {
        Map<String, AdmissionTrack> tracks = admissionTrackRepository
            .findAllByUniversityIdAndAdmissionYearOrderByNameAsc(university.getId(), admissionYear)
            .stream().collect(Collectors.toMap(item -> normalize(item.getName()), Function.identity(), (a, b) -> a));
        List<Long> trackIds = tracks.values().stream().map(AdmissionTrack::getId).toList();
        Map<String, RecruitmentUnit> units = new HashMap<>();
        if (!trackIds.isEmpty()) {
            for (RecruitmentUnit unit : recruitmentUnitRepository.findAllByAdmissionTrackIdInOrderByNameAsc(trackIds)) {
                units.put(unitKey(unit.getAdmissionTrack().getId(), unit.getCode(), unit.getName()), unit);
                units.put(unitNameKey(unit.getAdmissionTrack().getId(), unit.getName()), unit);
            }
        }
        int createdTracks = 0;
        int createdUnits = 0;

        List<ApplicationCandidate> applicationCandidates = new ArrayList<>();
        for (TransferApplicationRow row : rows) {
            String admissionTrackName = admissionTrackName(university, row);
            String trackKey = normalize(admissionTrackName);
            AdmissionTrack track = tracks.get(trackKey);
            if (track == null) {
                track = admissionTrackRepository.save(AdmissionTrack.create(university, admissionYear, admissionTrackName));
                tracks.put(trackKey, track);
                createdTracks++;
            }
            String unitLookup = unitKey(track.getId(), row.recruitmentUnitCode(), row.recruitmentUnitName());
            RecruitmentUnit unit = units.get(unitLookup);
            if (unit == null) unit = units.get(unitNameKey(track.getId(), row.recruitmentUnitName()));
            if (unit == null) {
                unit = recruitmentUnitRepository.save(RecruitmentUnit.create(
                    track, row.recruitmentUnitCode(), row.recruitmentUnitName()
                ));
                createdUnits++;
            } else if (!java.util.Objects.equals(unit.getCode(), clean(row.recruitmentUnitCode()))
                || !unit.getName().equals(row.recruitmentUnitName().trim()) || !unit.isActive()) {
                unit.update(row.recruitmentUnitCode(), row.recruitmentUnitName(), true);
            }
            units.put(unitLookup, unit);
            units.put(unitNameKey(track.getId(), row.recruitmentUnitName()), unit);
            applicationCandidates.add(new ApplicationCandidate(students.get(row.applicantNumber()), unit));
        }

        Set<String> seen = new HashSet<>();
        List<StudentApplication> created = new ArrayList<>();
        applicationCandidates.forEach(candidate -> {
            String key = applicationKey(candidate.student().getId(), candidate.unit().getId());
            if (seen.add(key)) {
                created.add(StudentApplication.create(candidate.student(), candidate.unit(), transcriptImport));
            }
        });
        studentApplicationRepository.saveAll(created);
        return new CatalogResult(createdTracks, createdUnits, created.size(), deletedApplications);
    }

    private String admissionTrackName(University university, TransferApplicationRow row) {
        if (!KBU_UNIVERSITY_CODE.equalsIgnoreCase(university.getCode())) return row.admissionTrackName();
        return canonicalKbuAdmissionType(row.recruitmentPeriodName(), row.admissionTrackName());
    }

    static String canonicalKbuAdmissionType(String periodName, String admissionTrackName) {
        String track = admissionTrackName == null ? "" : admissionTrackName.trim();
        track = track.replace("기회균형선발", "기회균형");
        String normalizedTrack = TextNormalizer.normalizePolicyText(track);
        if (normalizedTrack.startsWith("수시") || normalizedTrack.startsWith("정시")) return track;

        String period = TextNormalizer.normalizePolicyText(periodName);
        if (period.startsWith("수시")) return "수시 " + track;
        if (period.startsWith("정시")) {
            return normalizedTrack.equals("일반") ? "정시 일반(학생부)" : "정시 " + track;
        }
        return track;
    }

    CourseResult replaceCourses(
        List<TranscriptExcelRow> rows,
        Map<String, Student> students,
        String sourceFileName,
        StudentTranscriptImport transcriptImport,
        int deletedCourses
    ) {
        String sql = """
            INSERT INTO student_transcript_course (
                student_id, source_import_id, school_year, semester, subject_category, course_name, course_name_normalized,
                grade_value, grade_scale, achievement, raw_score, mean_score, standard_deviation,
                student_count, rank_position, tied_rank_count, legacy_achievement, credits,
                career_subject, professional_course, source_file_name, source_row_number, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """;
        int[][] results = jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> {
            statement.setLong(1, students.get(row.applicantNumber()).getId());
            statement.setLong(2, transcriptImport.getId());
            statement.setInt(3, row.schoolYear());
            statement.setInt(4, row.semester());
            statement.setString(5, row.subjectCategory().name());
            statement.setString(6, row.courseName());
            statement.setString(7, TextNormalizer.normalizeCourseName(row.courseName()));
            setInteger(statement, 8, row.grade());
            statement.setString(9, row.gradeScale().name());
            setString(statement, 10, row.achievement() == null ? null : row.achievement().name());
            setDecimal(statement, 11, row.rawScore());
            setDecimal(statement, 12, row.meanScore());
            setDecimal(statement, 13, row.standardDeviation());
            setInteger(statement, 14, row.studentCount());
            setInteger(statement, 15, row.rankPosition());
            setInteger(statement, 16, row.tiedRankCount());
            setString(statement, 17, row.legacyAchievement() == null ? null : row.legacyAchievement().name());
            statement.setBigDecimal(18, row.credits());
            statement.setBoolean(19, row.careerSubject());
            statement.setBoolean(20, row.professionalCourse());
            statement.setString(21, sourceFileName);
            statement.setInt(22, row.rowNumber());
        });
        CourseResult result = classifyBatchResults(results);
        if (result.unknown() > 0) {
            log.warn("Could not classify {} transcript course batch results", result.unknown());
        }
        return result.withDeleted(deletedCourses);
    }

    static CourseResult classifyBatchResults(int[][] results) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int unknown = 0;
        for (int[] batch : results) {
            for (int count : batch) {
                if (count == 1) created++;
                else if (count == 2) updated++;
                else if (count == 0) unchanged++;
                else if (count == Statement.SUCCESS_NO_INFO) unknown++;
                else unknown++;
            }
        }
        return new CourseResult(created, updated, unchanged, unknown, 0);
    }

    private void setInteger(java.sql.PreparedStatement statement, int index, Integer value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER); else statement.setInt(index, value);
    }

    private void setDecimal(java.sql.PreparedStatement statement, int index, BigDecimal value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.DECIMAL); else statement.setBigDecimal(index, value);
    }

    private void setString(java.sql.PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR); else statement.setString(index, value);
    }

    private String unitKey(Long trackId, String code, String name) {
        return trackId + ":" + normalize(code == null || code.isBlank() ? name : code);
    }

    private String unitNameKey(Long trackId, String name) {
        return trackId + ":name:" + normalize(name);
    }

    private String applicationKey(Long studentId, Long unitId) {
        return studentId + ":" + unitId;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeFileName(String value) {
        if (value == null || value.isBlank()) return "transfer.xlsx";
        String cleaned = value.replace('\\', '/');
        return cleaned.substring(cleaned.lastIndexOf('/') + 1);
    }

    record CatalogResult(
        int createdTracks,
        int createdUnits,
        int createdApplications,
        int deletedApplications
    ) {}
    record CourseResult(int created, int updated, int unchanged, int unknown, int deleted) {
        CourseResult withDeleted(int deleted) {
            return new CourseResult(created, updated, unchanged, unknown, deleted);
        }
    }
    private record ApplicationCandidate(Student student, RecruitmentUnit unit) {}
}
