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
        verify(jdbc).update(anyString(), eq("ZIP 구조·학년도·행 수·연결 정보를 확인해 주세요. 암호화 ZIP은 먼저 해제 후 일반 ZIP으로 다시 압축해 주세요."), eq(123L));
        assertThat(Files.exists(work)).isFalse();
    }
}
