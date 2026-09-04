package com.jinhakapply.gradevalidation.transcript.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MjcSourceWorkbookExtractorTest {

    @TempDir Path directory;

    @Test
    void extractsRequiredSheetsAsUtf8CsvWithoutLoadingTheWorkbookModel() throws Exception {
        Path workbookPath = directory.resolve("mjc.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            addSheet(workbook, "01_지원자정보", new String[] {"examNumber", "admissionTypeName"},
                new String[] {"MJC-SYN-001", "특별[일반고]"});
            addSheet(workbook, "04_학생부기본정보", new String[] {"examNumber", "graduateYear"},
                new String[] {"MJC-SYN-001", "2026"});
            addSheet(workbook, "05_교과학습발달상황_SubjectScore", new String[] {"examNumber", "subjectName"},
                new String[] {"MJC-SYN-001", "화법, 작문"});
            try (var output = Files.newOutputStream(workbookPath)) {
                workbook.write(output);
            }
        }

        Path extractionDirectory = Files.createDirectory(directory.resolve("extracted"));
        MjcSourceWorkbookExtractor.ExtractedBundle result =
            new MjcSourceWorkbookExtractor().extract(workbookPath, extractionDirectory);

        assertThat(Files.readString(result.applicantsFile())).contains("MJC-SYN-001,특별[일반고]");
        assertThat(Files.readString(result.baseInfoFile())).contains("MJC-SYN-001,2026");
        assertThat(Files.readString(result.subjectScoreFile())).contains("MJC-SYN-001,\"화법, 작문\"");
    }

    private void addSheet(XSSFWorkbook workbook, String name, String[] headers, String[] values) {
        var sheet = workbook.createSheet(name);
        var header = sheet.createRow(0);
        var data = sheet.createRow(1);
        for (int index = 0; index < headers.length; index++) {
            header.createCell(index).setCellValue(headers[index]);
            data.createCell(index).setCellValue(values[index]);
        }
    }
}
