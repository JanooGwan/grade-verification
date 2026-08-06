package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.jinhakapply.gradevalidation.global.code.ApiResponseCode;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.domain.StudentTranscriptImport;
import com.jinhakapply.gradevalidation.transcript.domain.TranscriptImportStatus;
import com.jinhakapply.gradevalidation.transcript.repository.StudentTranscriptImportRepository;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;

class SyuSourceImportServiceTest {

    @Test
    void marksThePersistedImportAsFailedWhenTheExecutorRejectsIt() {
        StudentTranscriptImportRepository repository = mock(StudentTranscriptImportRepository.class);
        SyuSourceImportProcessor processor = mock(SyuSourceImportProcessor.class);
        UniversityRepository universityRepository = mock(UniversityRepository.class);
        University university = University.create("SY", "삼육대학교");
        ReflectionTestUtils.setField(university, "id", 1L);
        when(universityRepository.findById(1L)).thenReturn(java.util.Optional.of(university));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new TaskRejectedException("full"))
            .when(processor).process(any(), org.mockito.ArgumentMatchers.anyLong(), any(Integer.class), any(Path.class), any(String.class), any());
        SyuSourceImportService service = new SyuSourceImportService(repository, processor, universityRepository);
        MockMultipartFile file = new MockMultipartFile(
            "file", "source.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {1, 2, 3}
        );

        assertThatThrownBy(() -> service.queue(2026, 1L, file))
            .isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiResponseCode.SOURCE_IMPORT_QUEUE_FULL));

        ArgumentCaptor<StudentTranscriptImport> captor = ArgumentCaptor.forClass(StudentTranscriptImport.class);
        verify(repository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        StudentTranscriptImport failed = captor.getValue();
        assertThat(failed.getStatus()).isEqualTo(TranscriptImportStatus.FAILED);
        assertThat(failed.getTemporaryFilePath()).isNull();
        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(processor).process(any(), org.mockito.ArgumentMatchers.anyLong(), any(Integer.class), pathCaptor.capture(), any(String.class), any());
        assertThat(pathCaptor.getValue()).doesNotExist();
    }

    @Test
    void failsInterruptedImportsAndDeletesOnlyTheirOwnedTemporaryFiles() throws Exception {
        StudentTranscriptImportRepository repository = mock(StudentTranscriptImportRepository.class);
        University university = University.create("SY", "삼육대학교");
        Path temporaryFile = Files.createTempFile("syu-source-import-", ".xlsx");
        StudentTranscriptImport interrupted = StudentTranscriptImport.queue(
            university, 2026, "source.xlsx", "hash", SyuSourceExcelStreamer.SOURCE_FORMAT,
            temporaryFile.toAbsolutePath().toString()
        );
        when(repository.findAllBySourceFormatAndStatusIn(
            SyuSourceExcelStreamer.SOURCE_FORMAT,
            List.of(TranscriptImportStatus.QUEUED, TranscriptImportStatus.PROCESSING)
        )).thenReturn(List.of(interrupted));

        new SyuSourceImportRecovery(repository).failInterruptedImports();

        assertThat(interrupted.getStatus()).isEqualTo(TranscriptImportStatus.FAILED);
        assertThat(interrupted.getTemporaryFilePath()).isNull();
        assertThat(temporaryFile).doesNotExist();
    }
}
