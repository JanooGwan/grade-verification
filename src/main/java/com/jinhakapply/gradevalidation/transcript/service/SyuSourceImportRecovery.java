package com.jinhakapply.gradevalidation.transcript.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
class SyuSourceImportRecovery {

    private static final String RECOVERY_MESSAGE =
        "애플리케이션 재시작으로 작업이 중단되었습니다. 원천 파일을 다시 업로드해 주세요.";

    private final StudentTranscriptImportRepository importRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedImports() {
        List<StudentTranscriptImport> interrupted = importRepository.findAllBySourceFormatAndStatusIn(
            SyuSourceExcelStreamer.SOURCE_FORMAT,
            List.of(TranscriptImportStatus.QUEUED, TranscriptImportStatus.PROCESSING)
        );
        for (StudentTranscriptImport transcriptImport : interrupted) {
            deleteOwnedTemporaryFile(transcriptImport.getTemporaryFilePath());
            transcriptImport.fail(RECOVERY_MESSAGE);
        }
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted SYU source imports as failed", interrupted.size());
        }
    }

    private void deleteOwnedTemporaryFile(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return;
        try {
            Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            Path candidate = Path.of(storedPath).toAbsolutePath().normalize();
            if (candidate.startsWith(temporaryRoot)
                && candidate.getFileName().toString().startsWith("syu-source-import-")) {
                Files.deleteIfExists(candidate);
            } else {
                log.warn("Skipped deleting an unowned source import path: {}", candidate);
            }
        } catch (Exception exception) {
            log.warn("Could not delete an interrupted source import temporary file", exception);
        }
    }
}
