package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class MjcSourceArchiveProcessorTest {
    @TempDir Path directory;

    @Test
    void invalidArchiveNeverEntersReplacementTransactionAndDoesNotExposeExceptionValues() throws Exception {
        var extractor = mock(MjcSourceArchiveExtractor.class);
        var jdbc = mock(JdbcTemplate.class);
        var transactions = mock(TransactionTemplate.class);
        var snapshots = mock(TranscriptSnapshotReplacementService.class);
        Path work = Files.createDirectory(directory.resolve("synthetic-work"));
        Path zip = work.resolve("synthetic.zip");
        Files.writeString(zip, "not an archive");
        when(extractor.extract(zip, work, 2027)).thenThrow(new IllegalArgumentException("SYNTHETIC-PRIVATE-VALUE"));
        var processor = new MjcSourceImportProcessor(new MjcSourceCsvReader(), new MjcSourceWorkbookExtractor(),
            jdbc, transactions, snapshots, extractor, new ObjectMapper());
        processor.processArchive(123L, 1L, 2027, work, zip);
        verifyNoInteractions(transactions, snapshots);
        var message = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), message.capture(), eq(123L));
        assertThat(message.getValue()).contains("단계=ARCHIVE_VALIDATION", "유형=IllegalArgumentException")
            .doesNotContain("SYNTHETIC-PRIVATE-VALUE");
        assertThat(Files.exists(work)).isFalse();
    }
}
