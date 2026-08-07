package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.SOURCE_IMPORT_QUEUE_FULL;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.dto.SourceImportStartResponse;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SyuSourceImportService {
    private static final long MAX_SOURCE_FILE_SIZE = 200L * 1024 * 1024;

    private final StudentTranscriptImportRepository importRepository;
    private final SyuSourceImportProcessor processor;
    private final UniversityRepository universityRepository;

    public SourceImportStartResponse queue(int admissionYear, Long universityId, MultipartFile file) {
        validate(admissionYear, file);
        University university = universityRepository.findById(universityId)
            .orElseThrow(() -> CustomException.of(com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND));
        if (!"SY".equals(university.getCode())) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "삼육대 원천양식은 삼육대학교를 선택했을 때만 업로드할 수 있습니다.");
        }
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("syu-source-import-", ".xlsx");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            String fileName = safeFileName(file.getOriginalFilename());
            StudentTranscriptImport queued = importRepository.saveAndFlush(StudentTranscriptImport.queue(
                university, admissionYear, fileName, HexFormat.of().formatHex(digest.digest()),
                SyuSourceExcelStreamer.SOURCE_FORMAT, temporaryFile.toAbsolutePath().toString()
            ));
            try {
                processor.process(
                    queued.getId(), university.getId(), admissionYear, temporaryFile, fileName
                );
            } catch (TaskRejectedException exception) {
                queued.fail("처리 대기열이 가득 차 작업을 시작하지 못했습니다. 잠시 후 다시 업로드해 주세요.");
                importRepository.saveAndFlush(queued);
                throw CustomException.of(SOURCE_IMPORT_QUEUE_FULL);
            }
            temporaryFile = null;
            return new SourceImportStartResponse(
                queued.getId(), queued.getStatus(), SyuSourceExcelStreamer.SOURCE_FORMAT,
                "대용량 원천 파일 가져오기를 시작했습니다. 최근 가져오기에서 진행 상태를 확인할 수 있습니다."
            );
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "원천 파일을 임시 저장하지 못했습니다.");
        } finally {
            if (temporaryFile != null) {
                try { Files.deleteIfExists(temporaryFile); } catch (Exception ignored) { }
            }
        }
    }

    private void validate(int admissionYear, MultipartFile file) {
        if (admissionYear < 2000 || admissionYear > 2100) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "모집연도는 2000~2100 사이여야 합니다.");
        }
        if (file == null || file.isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "업로드 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_SOURCE_FILE_SIZE) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "원천 파일은 200MB 이하여야 합니다.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "삼육대 원천 파일은 .xlsx 형식이어야 합니다.");
        }
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) return "syu-source.xlsx";
        String cleaned = Path.of(name).getFileName().toString();
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(cleaned.length() - 255);
    }
}
