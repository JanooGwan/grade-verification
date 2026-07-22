package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;

import java.math.BigDecimal;
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
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportMode;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportResponse;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptPreviewResponse;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
class TransferImportService {

    private static final int BATCH_SIZE = 1_000;

    private final TransferExcelParser parser;
    private final UniversityRepository universityRepository;
    private final AdmissionTrackRepository admissionTrackRepository;
    private final RecruitmentUnitRepository recruitmentUnitRepository;
    private final StudentApplicationRepository studentApplicationRepository;
    private final StudentRepository studentRepository;
    private final StudentTranscriptImportRepository importRepository;
    private final JdbcTemplate jdbcTemplate;

    TranscriptPreviewResponse preview(MultipartFile file, String fileSha256) {
        TransferExcelParseResult result = parser.parse(file);
        return new TranscriptPreviewResponse(
            file.getOriginalFilename(),
            fileSha256,
            result.sourceFormat(),
            result.applications().size(),
            result.totalRows(),
            result.courses().size(),
            result.invalidRows(),
            result.skippedRows(),
            result.courses().stream().limit(50).map(row -> new TranscriptPreviewResponse.PreviewRow(
                row.rowNumber(), row.applicantNumber(), row.studentName(), row.schoolYear(), row.semester(),
                row.subjectCategory(), row.courseName(), row.grade(), row.achievement(), row.credits()
            )).toList(),
            result.errors(),
            result.warnings()
        );
    }

    @Transactional
    TranscriptImportResponse importExcel(
        int admissionYear,
        Long universityId,
        TranscriptImportMode mode,
        MultipartFile file,
        String fileSha256
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
        if (mode == TranscriptImportMode.ALL_OR_NOTHING && result.invalidRows() > 0) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "오류 행이 %,d건 있어 전체 저장을 취소했습니다.".formatted(result.invalidRows()));
        }

        Set<String> applicantNumbers = result.courses().stream()
            .map(TranscriptExcelRow::applicantNumber)
            .collect(Collectors.toCollection(HashSet::new));
        applicantNumbers.addAll(result.applications().stream()
            .map(TransferApplicationRow::applicantNumber).toList());
        Map<String, TransferApplicationRow> applicationByApplicant = result.applications().stream()
            .collect(Collectors.toMap(TransferApplicationRow::applicantNumber, Function.identity(), (first, ignored) -> first));

        Map<String, Student> students = studentRepository.findAllByAdmissionYearAndApplicantNumberIn(
            admissionYear, applicantNumbers
        ).stream().collect(Collectors.toMap(Student::getApplicantNumber, Function.identity()));
        int updatedStudents = students.size();
        int createdStudents = 0;
        for (String applicantNumber : applicantNumbers) {
            Student existing = students.get(applicantNumber);
            TransferApplicationRow application = applicationByApplicant.get(applicantNumber);
            if (existing == null) {
                Student created = studentRepository.save(Student.create(
                    admissionYear,
                    applicantNumber,
                    "미등록",
                    null,
                    null,
                    application == null ? null : application.graduationYear()
                ));
                students.put(applicantNumber, created);
                createdStudents++;
            } else if (application != null && application.graduationYear() != null) {
                existing.updateProfile(existing.getName(), existing.getHighSchoolCode(), existing.getHighSchoolName(),
                    application.graduationYear());
            }
        }

        CatalogResult catalog = importApplications(university, admissionYear, result.applications(), students);
        CourseResult courses = upsertCourses(result.courses(), students, safeFileName(file.getOriginalFilename()));
        StudentTranscriptImport transcriptImport = importRepository.save(StudentTranscriptImport.create(
            admissionYear,
            safeFileName(file.getOriginalFilename()),
            mode,
            fileSha256,
            result.totalRows(),
            result.courses().size(),
            result.invalidRows()
        ));
        return new TranscriptImportResponse(
            transcriptImport.getId(), transcriptImport.getStatus(), result.sourceFormat(),
            result.totalRows(), result.courses().size(), result.invalidRows(),
            result.skippedRows(),
            createdStudents, updatedStudents, courses.created(), courses.updated(),
            result.applications().size(), catalog.createdApplications(), catalog.createdTracks(),
            catalog.createdUnits(), result.errors(), result.warnings()
        );
    }

    private CatalogResult importApplications(
        University university,
        int admissionYear,
        List<TransferApplicationRow> rows,
        Map<String, Student> students
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
            String trackKey = normalize(row.admissionTrackName());
            AdmissionTrack track = tracks.get(trackKey);
            if (track == null) {
                track = admissionTrackRepository.save(AdmissionTrack.create(university, admissionYear, row.admissionTrackName()));
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

        List<Long> studentIds = students.values().stream().map(Student::getId).toList();
        Set<String> existing = studentIds.isEmpty() ? new HashSet<>()
            : studentApplicationRepository.findAllByStudent_IdIn(studentIds).stream()
                .map(item -> applicationKey(item.getStudent().getId(), item.getRecruitmentUnit().getId()))
                .collect(Collectors.toCollection(HashSet::new));
        List<StudentApplication> created = new ArrayList<>();
        applicationCandidates.forEach(candidate -> {
            String key = applicationKey(candidate.student().getId(), candidate.unit().getId());
            if (existing.add(key)) created.add(StudentApplication.create(candidate.student(), candidate.unit()));
        });
        studentApplicationRepository.saveAll(created);
        return new CatalogResult(createdTracks, createdUnits, created.size());
    }

    private CourseResult upsertCourses(
        List<TranscriptExcelRow> rows,
        Map<String, Student> students,
        String sourceFileName
    ) {
        String sql = """
            INSERT INTO student_transcript_course (
                student_id, school_year, semester, subject_category, course_name,
                grade_value, grade_scale, achievement, raw_score, mean_score, standard_deviation,
                student_count, rank_position, tied_rank_count, legacy_achievement, credits,
                career_subject, professional_course, source_file_name, source_row_number, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE
                grade_value=VALUES(grade_value), grade_scale=VALUES(grade_scale), achievement=VALUES(achievement),
                raw_score=VALUES(raw_score), mean_score=VALUES(mean_score), standard_deviation=VALUES(standard_deviation),
                student_count=VALUES(student_count), rank_position=VALUES(rank_position),
                tied_rank_count=VALUES(tied_rank_count), legacy_achievement=VALUES(legacy_achievement),
                credits=VALUES(credits), career_subject=VALUES(career_subject),
                professional_course=VALUES(professional_course), source_file_name=VALUES(source_file_name),
                source_row_number=VALUES(source_row_number), updated_at=CURRENT_TIMESTAMP(6)
            """;
        int created = 0;
        int updated = 0;
        int[][] results = jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> {
            statement.setLong(1, students.get(row.applicantNumber()).getId());
            statement.setInt(2, row.schoolYear());
            statement.setInt(3, row.semester());
            statement.setString(4, row.subjectCategory().name());
            statement.setString(5, row.courseName());
            setInteger(statement, 6, row.grade());
            statement.setString(7, row.gradeScale().name());
            setString(statement, 8, row.achievement() == null ? null : row.achievement().name());
            setDecimal(statement, 9, row.rawScore());
            setDecimal(statement, 10, row.meanScore());
            setDecimal(statement, 11, row.standardDeviation());
            setInteger(statement, 12, row.studentCount());
            setInteger(statement, 13, row.rankPosition());
            setInteger(statement, 14, row.tiedRankCount());
            setString(statement, 15, row.legacyAchievement() == null ? null : row.legacyAchievement().name());
            statement.setBigDecimal(16, row.credits());
            statement.setBoolean(17, row.careerSubject());
            statement.setBoolean(18, row.professionalCourse());
            statement.setString(19, sourceFileName);
            statement.setInt(20, row.rowNumber());
        });
        for (int[] batch : results) {
            for (int count : batch) {
                if (count == 2) updated++;
                else created++;
            }
        }
        return new CourseResult(created, updated);
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

    private record CatalogResult(int createdTracks, int createdUnits, int createdApplications) {}
    private record CourseResult(int created, int updated) {}
    private record ApplicationCandidate(Student student, RecruitmentUnit unit) {}
}
