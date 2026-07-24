package com.jinhakapply.gradevalidation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import com.jinhakapply.gradevalidation.admission.domain.AdmissionTrack;
import com.jinhakapply.gradevalidation.admission.domain.RecruitmentUnit;
import com.jinhakapply.gradevalidation.admission.domain.StudentApplication;
import com.jinhakapply.gradevalidation.admission.repository.AdmissionTrackRepository;
import com.jinhakapply.gradevalidation.admission.repository.RecruitmentUnitRepository;
import com.jinhakapply.gradevalidation.admission.repository.StudentApplicationRepository;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleExtraction;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleExtractionRepository;
import com.jinhakapply.gradevalidation.transcript.domain.Student;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptCourse;
import com.jinhakapply.gradevalidation.transcript.domain.StudentAttendance;
import com.jinhakapply.gradevalidation.transcript.domain.StudentSchoolViolenceAction;
import com.jinhakapply.gradevalidation.transcript.domain.EducationBackground;
import com.jinhakapply.gradevalidation.transcript.domain.GraduationStatus;
import com.jinhakapply.gradevalidation.transcript.repository.StudentCourseSummaryProjection;
import com.jinhakapply.gradevalidation.transcript.repository.StudentRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptCourseRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentAttendanceRepository;
import com.jinhakapply.gradevalidation.transcript.repository.StudentSchoolViolenceActionRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class MySqlRepositoryIntegrationTest {

    private static final String MYSQL_PASSWORD = UUID.randomUUID().toString();

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
        .withDatabaseName("grade_validation")
        .withUsername("grade_app")
        .withPassword(MYSQL_PASSWORD)
        .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", MYSQL::getJdbcUrl);
        registry.add("DB_USERNAME", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UniversityRepository universityRepository;
    @Autowired AdmissionTrackRepository trackRepository;
    @Autowired RecruitmentUnitRepository unitRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired StudentTranscriptCourseRepository courseRepository;
    @Autowired StudentAttendanceRepository attendanceRepository;
    @Autowired StudentSchoolViolenceActionRepository schoolViolenceRepository;
    @Autowired StudentApplicationRepository applicationRepository;
    @Autowired EvaluationRuleExtractionRepository extractionRepository;
    @Autowired EntityManager entityManager;

    @Test
    void appliesEveryFlywayMigrationAndUsesDraftAsRuleDefault() {
        List<String> appliedVersions = jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank",
            String.class
        );
        String statusDefault = jdbcTemplate.queryForObject("""
            SELECT COLUMN_DEFAULT
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'evaluation_rule'
              AND COLUMN_NAME = 'status'
            """, String.class);
        List<String> legacySummaryUniqueColumns = jdbcTemplate.queryForList("""
            SELECT COLUMN_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'student_legacy_grade_summary'
              AND INDEX_NAME = 'uk_student_legacy_grade_summary'
            ORDER BY SEQ_IN_INDEX
            """, String.class);

        assertThat(appliedVersions).containsExactly(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
            "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27"
        );
        assertThat(statusDefault).isEqualTo("DRAFT");
        assertThat(legacySummaryUniqueColumns).containsExactly(
            "student_id", "summary_type", "school_year", "semester_key"
        );
    }

    @Test
    void normalizesHanshin2027TranscriptScoresToOneThousandPoints() {
        List<BigDecimal> multipliers = jdbcTemplate.queryForList("""
            SELECT rule.score_multiplier
            FROM evaluation_rule rule
            JOIN university university ON university.id = rule.university_id
            WHERE university.code = 'HS'
              AND rule.admission_year = 2027
              AND rule.status = 'PUBLISHED'
              AND rule.admission_type IN (
                  '학생부우수자', '학교장추천', '사회배려자', '고른기회', '기회균형선발',
                  '농어촌학생', '특성화고교졸업자', '참인재', '논술', '체육실기'
              )
            """, BigDecimal.class);

        assertThat(multipliers).hasSize(10).allMatch(
            multiplier -> multiplier.compareTo(new BigDecimal("10.0000")) == 0
        );
    }

    @Test
    void documentsCommonAdmissionTables() {
        Map<String, String> comments = jdbcTemplate.query(
            """
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('admission_track', 'student_attendance', 'student_school_violence_action')
                """,
            resultSet -> {
                Map<String, String> result = new HashMap<>();
                while (resultSet.next()) {
                    result.put(resultSet.getString("TABLE_NAME"), resultSet.getString("TABLE_COMMENT"));
                }
                return result;
            }
        );

        assertThat(comments).containsExactlyInAnyOrderEntriesOf(Map.of(
            "admission_track", "대학·입학연도별 전형 카탈로그와 사용 여부를 관리한다.",
            "student_attendance", "지원자의 학년별 미인정 결석·지각·조퇴·결과 원천데이터를 관리한다.",
            "student_school_violence_action", "지원자의 학교폭력 조치호수·조치일·유효 여부와 비고를 관리한다."
        ));
    }

    @Test
    void persistsUniversityCommonStudentEvaluationData() {
        Student student = Student.create(2027, "COMMON-001", "공통지원자", null, null, 2026);
        student.updateCommonEvaluationProfile(
            EducationBackground.DOMESTIC_HIGH_SCHOOL, GraduationStatus.GRADUATE, null
        );
        studentRepository.saveAndFlush(student);
        StudentAttendance attendance = StudentAttendance.create(student, 1);
        attendance.update(2, 3, 1, 0);
        attendanceRepository.save(attendance);
        schoolViolenceRepository.save(StudentSchoolViolenceAction.create(
            student, 2, 4, LocalDate.of(2025, 5, 1), true, "통합 테스트"
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(attendanceRepository.findAllByStudent_IdOrderBySchoolYearAsc(student.getId()))
            .singleElement().satisfies(saved -> {
                assertThat(saved.getUnexcusedAbsenceDays()).isEqualTo(2);
                assertThat(saved.getUnexcusedTardyCount()).isEqualTo(3);
            });
        assertThat(schoolViolenceRepository
            .findAllByStudent_IdOrderBySchoolYearAscActionNumberAsc(student.getId()))
            .singleElement().satisfies(saved -> {
                assertThat(saved.getActionNumber()).isEqualTo(4);
                assertThat(saved.isActive()).isTrue();
            });
    }

    @Test
    void enforcesCaseInsensitiveUniversityCodeUniqueness() {
        universityRepository.saveAndFlush(University.create("TUK", "한국공학대학교"));

        assertThatThrownBy(() ->
            universityRepository.saveAndFlush(University.create("tuk", "중복 대학교")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesRecruitmentUnitCodeUniquenessWithinTrack() {
        University university = universityRepository.saveAndFlush(University.create("TUK", "한국공학대학교"));
        AdmissionTrack track = trackRepository.saveAndFlush(AdmissionTrack.create(
            university, 2027, "학생부교과"
        ));
        unitRepository.saveAndFlush(RecruitmentUnit.create(track, "CS01", "컴퓨터공학부"));

        assertThatThrownBy(() ->
            unitRepository.saveAndFlush(RecruitmentUnit.create(track, "CS01", "게임공학과")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesRuleExtractionFileUniquenessFromV11() {
        University university = universityRepository.saveAndFlush(University.create("TUK", "한국공학대학교"));
        extractionRepository.saveAndFlush(extraction(university, "a".repeat(64)));

        assertThatThrownBy(() -> extractionRepository.saveAndFlush(extraction(university, "a".repeat(64))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void batchesStudentAndCourseLookupsAndSummarizesGrades() {
        Student first = studentRepository.saveAndFlush(Student.create(
            2027, "A-001", "첫 번째 학생", null, null, 2027
        ));
        Student second = studentRepository.saveAndFlush(Student.create(
            2027, "A-002", "두 번째 학생", null, null, 2027
        ));
        courseRepository.saveAndFlush(course(first, "수학", 2));
        courseRepository.saveAndFlush(course(first, "영어", 4));
        courseRepository.saveAndFlush(course(second, "국어", 1));

        List<Student> students = studentRepository.findAllByAdmissionYearAndApplicantNumberIn(
            2027, List.of("A-001", "A-002")
        );
        List<StudentTranscriptCourse> courses = courseRepository.findAllByStudent_IdIn(
            students.stream().map(Student::getId).toList()
        );
        List<StudentCourseSummaryProjection> summaries = courseRepository.summarizeByStudentIds(
            students.stream().map(Student::getId).toList()
        );

        assertThat(students).extracting(Student::getApplicantNumber)
            .containsExactlyInAnyOrder("A-001", "A-002");
        assertThat(courses).hasSize(3);
        assertThat(summaries).anySatisfy(summary -> {
            assertThat(summary.getStudentId()).isEqualTo(first.getId());
            assertThat(summary.getCourseCount()).isEqualTo(2);
            assertThat(summary.getAverageGrade()).isEqualTo(3.0);
        });
    }

    @Test
    void cascadesStudentDeletionToCoursesAndApplications() {
        University university = universityRepository.saveAndFlush(University.create("TUK", "한국공학대학교"));
        AdmissionTrack track = trackRepository.saveAndFlush(AdmissionTrack.create(
            university, 2027, "학생부교과"
        ));
        RecruitmentUnit unit = unitRepository.saveAndFlush(RecruitmentUnit.create(
            track, "CS01", "컴퓨터공학부"
        ));
        Student student = studentRepository.saveAndFlush(Student.create(
            2027, "A-001", "학생", null, null, 2027
        ));
        courseRepository.saveAndFlush(course(student, "수학", 2));
        applicationRepository.saveAndFlush(StudentApplication.create(student, unit));

        entityManager.clear();
        int deletedRows = jdbcTemplate.update("DELETE FROM student WHERE id = ?", student.getId());

        assertThat(deletedRows).isOne();
        assertThat(courseRepository.count()).isZero();
        assertThat(applicationRepository.count()).isZero();
    }

    private EvaluationRuleExtraction extraction(University university, String hash) {
        return EvaluationRuleExtraction.create(
            university, 2027, "guideline.pdf", hash, 10, 10,
            SelectionStrategy.ALL_COURSES, null,
            List.of(new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("50")),
            true,
            List.of(new BigDecimal("100"), new BigDecimal("95")),
            List.of(), List.of(SubjectCategory.KOREAN, SubjectCategory.MATH),
            false, RoundingMode.HALF_UP, "1-3", new BigDecimal("0.9000"),
            List.of(), List.of()
        );
    }

    private StudentTranscriptCourse course(Student student, String name, int grade) {
        StudentTranscriptCourse course = StudentTranscriptCourse.create(
            student, 1, 1, SubjectCategory.MATH, name
        );
        course.updateScore(
            grade, null, null, null, null, null, new BigDecimal("3"),
            false, false, "integration.xlsx", 1
        );
        return course;
    }
}
