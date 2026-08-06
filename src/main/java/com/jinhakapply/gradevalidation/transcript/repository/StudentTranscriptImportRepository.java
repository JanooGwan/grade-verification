package com.jinhakapply.gradevalidation.transcript.repository;

import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTranscriptImportRepository extends JpaRepository<StudentTranscriptImport, Long> {
    List<StudentTranscriptImport> findTop50ByOrderByCreatedAtDesc();
    List<StudentTranscriptImport> findAllBySourceFormatAndStatusIn(
        String sourceFormat, List<TranscriptImportStatus> statuses
    );
}
