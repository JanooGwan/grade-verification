package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.SOURCE_IMPORT_QUEUE_FULL;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Comparator;
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
public class MjcSourceImportService {
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final long MAX_BUNDLE_SIZE = 205L * 1024 * 1024;

    private final StudentTranscriptImportRepository importRepository;
    private final MjcSourceImportProcessor processor;
    private final UniversityRepository universityRepository;

    public SourceImportStartResponse queue(
        int admissionYear,
        Long universityId,
        MultipartFile applicantsFile,
        MultipartFile baseInfoFile,
        MultipartFile subjectScoreFile
    ) {
        validate(admissionYear, applicantsFile, baseInfoFile, subjectScoreFile);
        University university = universityRepository.findById(universityId)
            .orElseThrow(() -> CustomException.of(
                com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND));
        if (!"MJC".equals(university.getCode())) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "명지전문대 원천 CSV는 명지전문대학교를 선택했을 때만 업로드할 수 있습니다.");
        }

        Path directory = null;
        try {
            directory = Files.createTempDirectory("mjc-source-import-");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path applicants = copy(applicantsFile, directory.resolve("01_applicants.csv"), digest, "01");
            Path baseInfo = copy(baseInfoFile, directory.resolve("04_base_info.csv"), digest, "04");
            Path subjects = copy(subjectScoreFile, directory.resolve("05_subject_scores.csv"), digest, "05");
            StudentTranscriptImport queued = importRepository.saveAndFlush(StudentTranscriptImport.queue(
                university, admissionYear, "MJC-2026-source-csv-bundle",
                HexFormat.of().formatHex(digest.digest()), MjcSourceCsvReader.SOURCE_FORMAT,
                directory.toAbsolutePath().toString()
            ));
            try {
                processor.process(queued.getId(), university.getId(), admissionYear, directory,
                    applicants, baseInfo, subjects);
            } catch (TaskRejectedException exception) {
                queued.fail("처리 대기열이 가득 차 작업을 시작하지 못했습니다. 잠시 후 다시 업로드해 주세요.");
                importRepository.saveAndFlush(queued);
                throw CustomException.of(SOURCE_IMPORT_QUEUE_FULL);
            }
            directory = null;
            return new SourceImportStartResponse(
                queued.getId(), queued.getStatus(), MjcSourceCsvReader.SOURCE_FORMAT,
                "명지전문대 대용량 CSV 가져오기를 시작했습니다. 최근 가져오기에서 진행 상태를 확인할 수 있습니다."
            );
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "명지전문대 CSV 묶음을 임시 저장하지 못했습니다.");
        } finally {
            if (directory != null) deleteDirectory(directory);
        }
    }

    public SourceImportStartResponse queueWorkbook(
        int admissionYear,
        Long universityId,
        MultipartFile file
    ) {
        validateWorkbook(admissionYear, file);
        University university = requireMjcUniversity(universityId);
        Path directory = null;
        try {
            directory = Files.createTempDirectory("mjc-source-workbook-");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path workbook = copy(file, directory.resolve("mjc-integrated.xlsx"), digest, "workbook");
            String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "MJC-2026-integrated.xlsx" : file.getOriginalFilename();
            StudentTranscriptImport queued = importRepository.saveAndFlush(StudentTranscriptImport.queue(
                university, admissionYear, originalName,
                HexFormat.of().formatHex(digest.digest()), MjcSourceWorkbookExtractor.SOURCE_FORMAT,
                directory.toAbsolutePath().toString()
            ));
            try {
                processor.processWorkbook(queued.getId(), university.getId(), admissionYear, directory, workbook);
            } catch (TaskRejectedException exception) {
                queued.fail("처리 대기열이 가득 차 작업을 시작하지 못했습니다. 잠시 후 다시 업로드해 주세요.");
                importRepository.saveAndFlush(queued);
                throw CustomException.of(SOURCE_IMPORT_QUEUE_FULL);
            }
            directory = null;
            return new SourceImportStartResponse(
                queued.getId(), queued.getStatus(), MjcSourceWorkbookExtractor.SOURCE_FORMAT,
                "명지전문대 통합 Excel 가져오기를 시작했습니다. 최근 가져오기에서 진행 상태를 확인할 수 있습니다."
            );
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "명지전문대 통합 Excel을 임시 저장하지 못했습니다.");
        } finally {
            if (directory != null) deleteDirectory(directory);
        }
    }

    private Path copy(MultipartFile file, Path target, MessageDigest digest, String label) throws Exception {
        digest.update(label.getBytes(StandardCharsets.UTF_8));
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private void validate(int admissionYear, MultipartFile... files) {
        if (admissionYear != 2026) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "명지전문대 원천 CSV는 2026학년도만 지원합니다.");
        }
        long totalSize = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw CustomException.of(INVALID_TRANSCRIPT_FILE, "필수 CSV 파일이 비어 있습니다.");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw CustomException.of(INVALID_TRANSCRIPT_FILE, "CSV 파일 하나의 크기는 200MB 이하여야 합니다.");
            }
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".csv")) {
                throw CustomException.of(INVALID_TRANSCRIPT_FILE, "명지전문대 원천 파일은 CSV 형식이어야 합니다.");
            }
            totalSize += file.getSize();
        }
        if (totalSize > MAX_BUNDLE_SIZE) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "CSV 묶음의 전체 크기는 205MB 이하여야 합니다.");
        }
    }

    private void validateWorkbook(int admissionYear, MultipartFile file) {
        if (admissionYear != 2026) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "명지전문대 통합 Excel은 2026학년도만 지원합니다.");
        }
        if (file == null || file.isEmpty()) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "통합 Excel 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "통합 Excel 파일은 200MB 이하여야 합니다.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "명지전문대 통합 파일은 .xlsx 형식이어야 합니다.");
        }
    }

    private University requireMjcUniversity(Long universityId) {
        University university = universityRepository.findById(universityId)
            .orElseThrow(() -> CustomException.of(
                com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND));
        if (!"MJC".equalsIgnoreCase(university.getCode())) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE,
                "명지전문대 원천 파일은 명지전문대학교를 선택했을 때만 업로드할 수 있습니다.");
        }
        return university;
    }

    static void deleteDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
