package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTranscriptImportRepository extends JpaRepository<StudentTranscriptImport, Long> {
    List<StudentTranscriptImport> findTop50ByOrderByCreatedAtDesc();
    List<StudentTranscriptImport> findTop50ByUniversity_IdOrderByCreatedAtDesc(Long universityId);
    java.util.Optional<StudentTranscriptImport> findTopByUniversity_IdAndAdmissionYearAndSourceFormatAndStatusInOrderByCreatedAtDesc(
        Long universityId, int admissionYear, String sourceFormat, List<TranscriptImportStatus> statuses
    );
    List<StudentTranscriptImport> findAllBySourceFormatAndStatusIn(
        String sourceFormat, List<TranscriptImportStatus> statuses
    );
}
