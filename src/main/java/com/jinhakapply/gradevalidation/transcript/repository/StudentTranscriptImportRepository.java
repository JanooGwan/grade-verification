package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTranscriptImportRepository extends JpaRepository<StudentTranscriptImport, Long> {
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT EXISTS(SELECT 1 FROM student_mjc_archive_applicant
                      WHERE import_id=:importId AND verification_code IS NOT NULL)
        """, nativeQuery = true)
    boolean hasMjcArchiveFailureResults(@org.springframework.data.repository.query.Param("importId") Long importId);

    List<StudentTranscriptImport> findTop50ByOrderByCreatedAtDesc();
    List<StudentTranscriptImport> findTop50ByUniversity_IdOrderByCreatedAtDesc(Long universityId);
    java.util.Optional<StudentTranscriptImport> findTopByUniversity_IdAndAdmissionYearOrderByCreatedAtDesc(
        Long universityId, int admissionYear
    );
    java.util.Optional<StudentTranscriptImport> findTopByUniversity_IdAndAdmissionYearAndStatusInOrderByCreatedAtDesc(
        Long universityId, int admissionYear, List<TranscriptImportStatus> statuses
    );
    List<StudentTranscriptImport> findAllBySourceFormatAndStatusIn(
        String sourceFormat, List<TranscriptImportStatus> statuses
    );
}
