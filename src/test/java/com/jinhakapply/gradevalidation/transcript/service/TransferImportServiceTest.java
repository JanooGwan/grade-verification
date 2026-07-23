package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Statement;

import org.junit.jupiter.api.Test;

class TransferImportServiceTest {

    @Test
    void classifiesCreatedUpdatedUnchangedAndUnknownBatchResultsSeparately() {
        TransferImportService.CourseResult result = TransferImportService.classifyBatchResults(new int[][] {
            {1, 2, 0, Statement.SUCCESS_NO_INFO, Statement.EXECUTE_FAILED}
        });

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.unknown()).isEqualTo(2);
    }
}
