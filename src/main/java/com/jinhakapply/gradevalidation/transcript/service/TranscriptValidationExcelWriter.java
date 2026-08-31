package com.jinhakapply.gradevalidation.transcript.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_TRANSCRIPT_FILE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.transcript.dto.TranscriptImportRowError;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

@Component
class TranscriptValidationExcelWriter {
    private static final String[] RESULT_HEADERS = {
        "지원정보 행", "수험번호", "전형명", "모집단위명",
        "등급×이수단위 합", "환산점수×이수단위 합", "총 반영 이수단위",
        "기준 환산점수", "전형별 교과 배율", "교과 반영점수(반올림 전)", "교과 반영점수",
        "교과성적(1,000점 만점)"
    };
    private static final String[] COURSE_COMPARISON_HEADERS = {
        "지원정보 행", "수험번호", "학생명", "전형명", "모집단위명",
        "반영 여부", "선택순번", "원본 성적 행", "학년", "학기", "교과", "과목명",
        "석차등급", "성취도", "이수단위", "환산점수", "가중점수",
        "진로선택", "전문교과", "원본 고교구분", "지원자 고교구분코드"
    };
    private static final String[] KBU_COURSE_COMPARISON_HEADERS = {
        "지원정보 행", "수험번호", "학생명", "전형명", "모집단위명",
        "반영 여부", "선택순번", "학년", "학기", "교과", "과목명",
        "석차등급", "성취도", "이수단위", "환산점수", "가중점수",
        "진로선택", "전문교과", "직업과정 위탁학기"
    };
    private static final String[] KBU_RESULT_HEADERS = {
        "지원정보 행", "수험번호", "전형명", "모집단위명", "검증 상태", "실패 코드", "실패 사유",
        "등급×이수단위 합", "환산점수×이수단위 합", "총 반영 이수단위", "기준 환산점수",
        "전형별 교과 배율", "교과 반영점수(반올림 전)", "교과 반영점수", "교과성적(1,000점 만점)"
    };
    private static final String[] INTERMEDIATE_HEADERS = {
        "지원정보 행", "수험번호", "전형명", "모집단위명", "최종 환산점수", "산출 구분", "교과·학기",
        "선택 기준", "선택 여부", "선택 순위", "과목 수", "이수단위 합", "등급×이수단위 합",
        "환산점수×이수단위 합", "평균환산점수"
    };

    byte[] write(
        String originalFileName,
        String sourceFormat,
        String universityName,
        int applicationRows,
        int totalRows,
        List<TranscriptImportRowError> skipped,
        List<TranscriptExcelRow> courses,
        List<TranscriptImportRowError> errors,
        List<String> warnings,
        TranscriptBatchVerificationResult verification
    ) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            write(
                originalFileName, sourceFormat, universityName, applicationRows, totalRows,
                skipped, courses, errors, warnings, verification, output
            );
            return output.toByteArray();
        } catch (IOException exception) {
            throw CustomException.of(INVALID_TRANSCRIPT_FILE, "검증 결과 Excel 파일을 생성하지 못했습니다.");
        }
    }

    void write(
        String originalFileName,
        String sourceFormat,
        String universityName,
        int applicationRows,
        int totalRows,
        List<TranscriptImportRowError> skipped,
        List<TranscriptExcelRow> courses,
        List<TranscriptImportRowError> errors,
        List<String> warnings,
        TranscriptBatchVerificationResult verification,
        OutputStream output
    ) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            Styles styles = new Styles(workbook);
            boolean kbu = isKbu(universityName);
            if (kbu) createKbuVerificationResultSheet(workbook, styles, universityName, verification);
            else createVerificationResultSheet(workbook, styles, universityName, verification);
            createIntermediateCalculationSheet(workbook, styles, universityName, verification);
            createCourseComparisonSheet(workbook, styles, courses, verification, kbu);
            createSummarySheet(
                workbook, styles, originalFileName, sourceFormat, applicationRows,
                totalRows, courses.size(), errors.size(), skipped.size(), warnings,
                verification, skipped, errors
            );
            workbook.write(output);
        }
    }

    private void createSummarySheet(
        SXSSFWorkbook workbook,
        Styles styles,
        String originalFileName,
        String sourceFormat,
        int applicationRows,
        int totalRows,
        int validRows,
        int invalidRows,
        int skippedRows,
        List<String> warnings,
        TranscriptBatchVerificationResult verification,
        List<TranscriptImportRowError> skipped,
        List<TranscriptImportRowError> errors
    ) {
        Sheet sheet = workbook.createSheet("검증 요약");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, "Excel 가져오기 검증 결과", 7);

        int rowIndex = 2;
        rowIndex = section(sheet, styles, rowIndex, "파일 정보");
        rowIndex = keyValue(sheet, styles, rowIndex, "파일명", originalFileName, "인식 형식", formatLabel(sourceFormat));
        rowIndex++;
        rowIndex = section(sheet, styles, rowIndex, "검증 집계");
        rowIndex = keyValue(sheet, styles, rowIndex, "지원정보", applicationRows, "전체 성적", totalRows);
        rowIndex = keyValue(sheet, styles, rowIndex, "정상", validRows, "제외", skippedRows);
        rowIndex = keyValue(sheet, styles, rowIndex, "오류", invalidRows, "DB 저장 가능", invalidRows == 0 ? "가능" : "저장 정책에 따름");
        rowIndex = keyValue(sheet, styles, rowIndex, "성적 검증 성공", verification.successes().size(),
            "성적 검증 실패", verification.failures().size());

        if (warnings != null && !warnings.isEmpty()) {
            rowIndex++;
            rowIndex = section(sheet, styles, rowIndex, "안내 사항");
            for (String warning : warnings) {
                Row row = sheet.createRow(rowIndex++);
                Cell cell = row.createCell(0);
                set(cell, warning, styles.warning);
                sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 7));
            }
        }

        if (!verification.failures().isEmpty()) {
            rowIndex++;
            rowIndex = section(sheet, styles, rowIndex, "성적 검증 실패 상세");
            rowIndex = tableHeader(sheet, styles, rowIndex, new String[] {
                "지원정보 행", "수험번호", "전형명", "모집단위명", "대상 과목 수", "실패 코드", "실패 사유"
            });
            for (TranscriptBatchVerificationResult.Failure failure : verification.failures()) {
                TransferApplicationRow application = failure.application();
                writeRow(sheet.createRow(rowIndex++), new Object[] {
                    application.rowNumber(), application.applicantNumber(), application.admissionTrackName(),
                    application.recruitmentUnitName(), failure.availableCourseCount(), failure.code(), failure.reason()
                }, styles, 5);
            }
        }
        rowIndex = appendImportIssues(sheet, styles, rowIndex, "가져오기 제외 상세", skipped, styles.warning);
        appendImportIssues(sheet, styles, rowIndex, "가져오기 오류 상세", errors, styles.error);

        int[] widths = {18, 24, 24, 28, 16, 22, 70, 18};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.createFreezePane(0, 2);
    }

    private int appendImportIssues(
        Sheet sheet,
        Styles styles,
        int rowIndex,
        String label,
        List<TranscriptImportRowError> issues,
        CellStyle reasonStyle
    ) {
        if (issues == null || issues.isEmpty()) return rowIndex;
        rowIndex++;
        rowIndex = section(sheet, styles, rowIndex, label);
        rowIndex = tableHeader(sheet, styles, rowIndex, new String[] {"원본 행", "처리 사유"});
        for (TranscriptImportRowError issue : issues) {
            Row row = sheet.createRow(rowIndex++);
            set(row.createCell(0), issue.rowNumber(), styles.integer);
            set(row.createCell(1), issue.reason(), reasonStyle);
        }
        return rowIndex;
    }

    private int tableHeader(Sheet sheet, Styles styles, int rowIndex, String[] headers) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(28);
        for (int column = 0; column < headers.length; column++) {
            set(row.createCell(column), headers[column], styles.header);
        }
        return rowIndex + 1;
    }

    private void createVerificationResultSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        String universityName,
        TranscriptBatchVerificationResult verification
    ) {
        Sheet sheet = workbook.createSheet("학생별 검증 결과");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, shortUniversityName(universityName)
            + " 교과성적 검증 결과 - 비교과·고사·학교폭력 미포함", RESULT_HEADERS.length - 1);
        header(sheet, styles, RESULT_HEADERS);
        List<TranscriptBatchVerificationResult.Success> results = new ArrayList<>(verification.successes());
        results.sort(Comparator.comparingInt(success -> success.application().rowNumber()));

        int rowIndex = 3;
        for (TranscriptBatchVerificationResult.Success success : results) {
            Row row = sheet.createRow(rowIndex++);
            TransferApplicationRow application = success.application();
            GradeVerificationResponse result = success.verification();
            GradeVerificationResponse.CalculationSummary summary = result.calculationSummary();
            Object[] values = {
                application.rowNumber(), application.applicantNumber(), application.admissionTrackName(),
                application.recruitmentUnitName(), summary.gradeTimesCreditsSum(),
                summary.convertedScoreTimesCreditsSum(), summary.totalIncludedCredits(),
                result.baseScore(), summary.scoreMultiplier(), summary.scoreBeforeFinalRounding(),
                result.finalScore(), thousandPointScore(result)
            };
            writeRow(row, values, styles, -1);
        }
        finishTable(sheet, results.size(), RESULT_HEADERS.length);
        setWidths(sheet, RESULT_HEADERS, Set.of(2, 3));
    }

    private void createKbuVerificationResultSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        String universityName,
        TranscriptBatchVerificationResult verification
    ) {
        Sheet sheet = workbook.createSheet("학생별 검증 결과");
        sheet.setDisplayGridlines(false);
        title(sheet, styles, shortUniversityName(universityName)
            + " 학생별 검증 결과 - 지원자당 1행", KBU_RESULT_HEADERS.length - 1);
        header(sheet, styles, KBU_RESULT_HEADERS);

        List<KbuResultRow> results = new ArrayList<>();
        verification.successes().forEach(success ->
            results.add(new KbuResultRow(success.application(), success, null)));
        verification.failures().forEach(failure ->
            results.add(new KbuResultRow(failure.application(), null, failure)));
        results.sort(Comparator.comparingInt(result -> result.application().rowNumber()));
        Map<String, KbuResultRow> resultByApplicant = new LinkedHashMap<>();
        results.forEach(result -> resultByApplicant.putIfAbsent(
            result.application().applicantNumber(), result
        ));
        List<KbuResultRow> deduplicatedResults = List.copyOf(resultByApplicant.values());

        int rowIndex = 3;
        for (KbuResultRow result : deduplicatedResults) {
            Row row = sheet.createRow(rowIndex++);
            TransferApplicationRow application = result.application();
            if (result.success() != null) {
                GradeVerificationResponse verificationResult = result.success().verification();
                GradeVerificationResponse.CalculationSummary summary = verificationResult.calculationSummary();
                writeRow(row, new Object[] {
                    application.rowNumber(), application.applicantNumber(), application.admissionTrackName(),
                    application.recruitmentUnitName(), "성공", null, null,
                    summary.gradeTimesCreditsSum(), summary.convertedScoreTimesCreditsSum(),
                    summary.totalIncludedCredits(), verificationResult.baseScore(), summary.scoreMultiplier(),
                    summary.scoreBeforeFinalRounding(), verificationResult.finalScore(),
                    thousandPointScore(verificationResult)
                }, styles, -1);
                row.getCell(4).setCellStyle(styles.selected);
                row.getCell(13).setCellStyle(styles.finalScore);
            } else {
                TranscriptBatchVerificationResult.Failure failure = result.failure();
                writeRow(row, new Object[] {
                    application.rowNumber(), application.applicantNumber(), application.admissionTrackName(),
                    application.recruitmentUnitName(), "실패", failure.code(), failure.reason(),
                    null, null, null, null, null, null, null, null
                }, styles, 6);
                row.getCell(4).setCellStyle(styles.error);
            }
        }
        finishTable(sheet, deduplicatedResults.size(), KBU_RESULT_HEADERS.length);
        sheet.createFreezePane(4, 3);
        setWidths(sheet, KBU_RESULT_HEADERS, Set.of(2, 3, 6));
    }

    private void createIntermediateCalculationSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        String universityName,
        TranscriptBatchVerificationResult verification
    ) {
        boolean hasIntermediateCalculations = verification.successes().stream()
            .anyMatch(success -> !success.intermediateCalculations().isEmpty());
        boolean kbu = isKbu(universityName);
        if (!hasIntermediateCalculations && !kbu) return;

        Sheet sheet = workbook.createSheet("성적 산출 중간값");
        sheet.setDisplayGridlines(false);
        title(
            sheet, styles,
            shortUniversityName(universityName)
                + " 성적 산출 중간값"
                + " - 일반학과 5개 학기 중 2개, 간호·보건 5개 교과 중 3개 선택",
            INTERMEDIATE_HEADERS.length - 1
        );
        header(sheet, styles, INTERMEDIATE_HEADERS);

        List<TranscriptBatchVerificationResult.Success> results = new ArrayList<>(verification.successes());
        results.sort(Comparator.comparingInt(success -> success.application().rowNumber()));
        int rowIndex = 3;
        for (TranscriptBatchVerificationResult.Success success : results) {
            for (TranscriptBatchVerificationResult.IntermediateCalculation calculation
                : success.intermediateCalculations()) {
                TransferApplicationRow application = success.application();
                Row row = sheet.createRow(rowIndex++);
                writeRow(row, new Object[] {
                    application.rowNumber(), application.applicantNumber(), application.admissionTrackName(),
                    application.recruitmentUnitName(), success.verification().finalScore(),
                    calculation.groupType(), calculation.groupName(),
                    selectionCriteria(calculation), calculation.selected() ? "선택됨" : "미선택",
                    calculation.selectionOrder(),
                    calculation.courseCount(), calculation.totalCredits(), calculation.gradeTimesCreditsSum(),
                    calculation.convertedScoreTimesCreditsSum(), calculation.averageConvertedScore()
                }, styles, -1);
                row.getCell(4).setCellStyle(styles.finalScore);
                if (calculation.selected()) row.getCell(8).setCellStyle(styles.selected);
            }
        }
        finishTable(sheet, rowIndex - 3, INTERMEDIATE_HEADERS.length);
        sheet.createFreezePane(7, 3);
        setWidths(sheet, INTERMEDIATE_HEADERS, Set.of(2, 3));
    }

    private String selectionCriteria(TranscriptBatchVerificationResult.IntermediateCalculation calculation) {
        return "학기".equals(calculation.groupType())
            ? "5개 학기 중 우수 2개"
            : "5개 교과 중 우수 3개";
    }

    private void createCourseComparisonSheet(
        SXSSFWorkbook workbook,
        Styles styles,
        List<TranscriptExcelRow> courses,
        TranscriptBatchVerificationResult verification,
        boolean kbu
    ) {
        String[] headers = kbu ? KBU_COURSE_COMPARISON_HEADERS : COURSE_COMPARISON_HEADERS;
        Sheet sheet = workbook.createSheet("학생별 과목 비교");
        sheet.setDisplayGridlines(false);
        title(
            sheet, styles, "학생별 전체 과목 및 반영 과목 비교 - 노란색 셀은 성적 계산에 선택된 과목",
            headers.length - 1
        );
        header(sheet, styles, headers);

        Map<String, List<TranscriptExcelRow>> coursesByApplicant = new HashMap<>();
        for (TranscriptExcelRow course : courses) {
            coursesByApplicant.computeIfAbsent(course.applicantNumber(), ignored -> new ArrayList<>()).add(course);
        }

        List<TranscriptBatchVerificationResult.Success> results = new ArrayList<>(verification.successes());
        results.sort(Comparator.comparingInt(success -> success.application().rowNumber()));
        int rowIndex = 3;
        for (TranscriptBatchVerificationResult.Success success : results) {
            List<TranscriptBatchVerificationResult.SelectedCourse> selectedCourses =
                new ArrayList<>(success.selectedCourses());
            selectedCourses.sort(selectedCourseComparator());

            Map<TranscriptExcelRow, TranscriptBatchVerificationResult.SelectedCourse> selectedByCourse =
                new HashMap<>();
            Map<TranscriptExcelRow, Integer> selectionOrderByCourse = new HashMap<>();
            for (int index = 0; index < selectedCourses.size(); index++) {
                TranscriptBatchVerificationResult.SelectedCourse selected = selectedCourses.get(index);
                selectedByCourse.put(selected.source(), selected);
                selectionOrderByCourse.put(selected.source(), index + 1);
            }

            List<TranscriptExcelRow> applicantCourses = new ArrayList<>(coursesByApplicant.getOrDefault(
                success.application().applicantNumber(), List.of()
            ));
            applicantCourses.sort(courseComparator());
            for (TranscriptExcelRow course : applicantCourses) {
                TranscriptBatchVerificationResult.SelectedCourse selected = selectedByCourse.get(course);
                Row row = sheet.createRow(rowIndex++);
                writeCourseComparisonRow(
                    row, styles, success.application(), success.studentName(), success.schoolInfo(), course, kbu,
                    selected == null ? "미선택" : "선택됨", selectionOrderByCourse.get(course), selected
                );
                if (selected != null) row.getCell(5).setCellStyle(styles.selected);
            }
        }

        List<TranscriptBatchVerificationResult.Failure> failures = new ArrayList<>(verification.failures());
        failures.sort(Comparator.comparingInt(failure -> failure.application().rowNumber()));
        for (TranscriptBatchVerificationResult.Failure failure : failures) {
            List<TranscriptExcelRow> applicantCourses = new ArrayList<>(coursesByApplicant.getOrDefault(
                failure.application().applicantNumber(), List.of()
            ));
            applicantCourses.sort(courseComparator());
            for (TranscriptExcelRow course : applicantCourses) {
                Row row = sheet.createRow(rowIndex++);
                writeCourseComparisonRow(
                    row, styles, failure.application(), failure.studentName(), null, course, kbu,
                    "검증 실패", null, null
                );
                row.getCell(5).setCellStyle(styles.error);
            }
        }

        finishTable(sheet, rowIndex - 3, headers.length);
        sheet.createFreezePane(6, 3);
        setWidths(sheet, headers, kbu ? Set.of(3, 4, 10) : Set.of(3, 4, 11, 19, 20));
        sheet.setColumnWidth(2, 24 * 256);
    }

    private void writeCourseComparisonRow(
        Row row,
        Styles styles,
        TransferApplicationRow application,
        String studentName,
        ApplicantSchoolInfoRow schoolInfo,
        TranscriptExcelRow course,
        boolean kbu,
        String status,
        Integer selectionOrder,
        TranscriptBatchVerificationResult.SelectedCourse selected
    ) {
        GradeVerificationResponse.CourseCalculation calculation = selected == null ? null : selected.calculation();
        Object[] values = kbu
            ? new Object[] {
                application.rowNumber(), application.applicantNumber(), studentName,
                application.admissionTrackName(), application.recruitmentUnitName(),
                status, selectionOrder, course.schoolYear(), course.semester(),
                subjectCategoryLabel(course.subjectCategory()), course.courseName(), course.grade(),
                course.achievement(), course.credits(),
                calculation == null ? null : calculation.convertedScore(),
                calculation == null ? null : calculation.weightedScore(),
                course.careerSubject() ? "Y" : "N", course.professionalCourse() ? "Y" : "N",
                course.vocationalTrainingSemester() ? "Y" : "N"
            }
            : new Object[] {
                application.rowNumber(), application.applicantNumber(), studentName,
                application.admissionTrackName(), application.recruitmentUnitName(),
                status, selectionOrder, course.rowNumber(), course.schoolYear(), course.semester(),
                subjectCategoryLabel(course.subjectCategory()), course.courseName(), course.grade(),
                course.achievement(), course.credits(),
                calculation == null ? null : calculation.convertedScore(),
                calculation == null ? null : calculation.weightedScore(),
                course.careerSubject() ? "Y" : "N", course.professionalCourse() ? "Y" : "N",
                schoolInfo == null ? null : schoolInfo.sourceHighSchoolCategory(),
                schoolInfo == null ? null : schoolInfo.applicantHighSchoolCategoryCode()
            };
        writeRow(row, values, styles, -1);
    }

    private String subjectCategoryLabel(SubjectCategory category) {
        if (category == null) return "";
        return switch (category) {
            case KOREAN -> "국어";
            case MATH -> "수학";
            case ENGLISH -> "영어";
            case SOCIAL -> "사회";
            case SCIENCE -> "과학";
            case OTHER -> "기타";
        };
    }

    private Comparator<TranscriptExcelRow> courseComparator() {
        return Comparator.comparingInt(TranscriptExcelRow::schoolYear)
            .thenComparingInt(TranscriptExcelRow::semester)
            .thenComparingInt(TranscriptExcelRow::rowNumber);
    }

    private Comparator<TranscriptBatchVerificationResult.SelectedCourse> selectedCourseComparator() {
        return Comparator
            .comparing(
                (TranscriptBatchVerificationResult.SelectedCourse selected) ->
                    selected.calculation().effectiveGrade(),
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .thenComparing(
                selected -> selected.calculation().appliedCredits(),
                Comparator.nullsLast(Comparator.reverseOrder())
            )
            .thenComparing(
                selected -> selected.source().schoolYear(),
                Comparator.reverseOrder()
            )
            .thenComparing(
                selected -> selected.source().semester(),
                Comparator.reverseOrder()
            )
            .thenComparingInt(selected -> selected.source().rowNumber());
    }

    private void writeRow(Row row, Object[] values, Styles styles, int errorFromColumn) {
        for (int column = 0; column < values.length; column++) {
            Object value = values[column];
            CellStyle style;
            if ("성공".equals(value)) style = styles.success;
            else if ("실패".equals(value)) style = styles.error;
            else if (errorFromColumn >= 0 && column >= errorFromColumn) style = styles.error;
            else if (value instanceof Integer || value instanceof Long) style = styles.integer;
            else if (value instanceof Number) style = styles.decimal;
            else style = styles.text;
            set(row.createCell(column), value, style);
        }
    }

    private void finishTable(Sheet sheet, int dataRows, int columns) {
        sheet.createFreezePane(0, 3);
        if (dataRows > 0) {
            sheet.setAutoFilter(new CellRangeAddress(2, dataRows + 2, 0, columns - 1));
        }
    }

    private void setWidths(Sheet sheet, String[] headers, Set<Integer> wideColumns) {
        for (int column = 0; column < headers.length; column++) {
            int width = wideColumns.contains(column) ? 42 : Math.max(12, Math.min(22, headers[column].length() + 5));
            sheet.setColumnWidth(column, width * 256);
        }
    }

    private BigDecimal thousandPointScore(GradeVerificationResponse result) {
        if (result.baseScore() == null) return null;
        GradeVerificationResponse.CalculationSummary summary = result.calculationSummary();
        return result.baseScore().multiply(BigDecimal.TEN)
            .setScale(summary.finalScale(), summary.finalRounding());
    }

    private void title(Sheet sheet, Styles styles, String value, int lastColumn) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(30);
        Cell cell = row.createCell(0);
        set(cell, value, styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
    }

    private void header(Sheet sheet, Styles styles, String[] headers) {
        Row row = sheet.createRow(2);
        row.setHeightInPoints(28);
        for (int index = 0; index < headers.length; index++) {
            set(row.createCell(index), headers[index], styles.header);
        }
    }

    private int section(Sheet sheet, Styles styles, int rowIndex, String value) {
        Row row = sheet.createRow(rowIndex);
        set(row.createCell(0), value, styles.section);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 7));
        return rowIndex + 1;
    }

    private int keyValue(
        Sheet sheet,
        Styles styles,
        int rowIndex,
        String firstKey,
        Object firstValue,
        String secondKey,
        Object secondValue
    ) {
        Row row = sheet.createRow(rowIndex);
        set(row.createCell(0), firstKey, styles.key);
        set(row.createCell(1), firstValue, firstValue instanceof Integer || firstValue instanceof Long
            ? styles.integer : styles.value);
        set(row.createCell(2), secondKey, styles.key);
        set(row.createCell(3), secondValue, secondValue instanceof Integer || secondValue instanceof Long
            ? styles.integer : styles.value);
        return rowIndex + 1;
    }

    private void set(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value == null ? "" : value.toString());
    }

    private String formatLabel(String sourceFormat) {
        if ("HANSHIN_MULTI_SHEET_V1".equals(sourceFormat)) return "한신대 전달양식";
        if ("KOREAN_MULTI_SHEET_V1".equals(sourceFormat)) return "대학 전달양식";
        return "표준 성적양식";
    }

    private String shortUniversityName(String universityName) {
        if (universityName == null || universityName.isBlank()) return "대학";
        String name = universityName.trim();
        if (name.endsWith("대학교")) return name.substring(0, name.length() - "대학교".length()) + "대";
        return name;
    }

    private boolean isKbu(String universityName) {
        return "경복대".equals(shortUniversityName(universityName));
    }

    private record KbuResultRow(
        TransferApplicationRow application,
        TranscriptBatchVerificationResult.Success success,
        TranscriptBatchVerificationResult.Failure failure
    ) {
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle key;
        private final CellStyle value;
        private final CellStyle header;
        private final CellStyle text;
        private final CellStyle integer;
        private final CellStyle decimal;
        private final CellStyle warning;
        private final CellStyle error;
        private final CellStyle success;
        private final CellStyle selected;
        private final CellStyle finalScore;

        private Styles(SXSSFWorkbook workbook) {
            title = style(workbook, "174A37", IndexedColors.WHITE.getIndex(), true, 16);
            title.setAlignment(HorizontalAlignment.LEFT);
            section = style(workbook, "DDECE2", IndexedColors.DARK_GREEN.getIndex(), true, 11);
            key = bordered(workbook, "EEF5F0", IndexedColors.DARK_GREEN.getIndex(), true);
            value = bordered(workbook, null, IndexedColors.BLACK.getIndex(), false);
            header = bordered(workbook, "2E6849", IndexedColors.WHITE.getIndex(), true);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setWrapText(true);
            text = bordered(workbook, null, IndexedColors.BLACK.getIndex(), false);
            integer = bordered(workbook, null, IndexedColors.BLACK.getIndex(), false);
            integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            decimal = bordered(workbook, null, IndexedColors.BLACK.getIndex(), false);
            decimal.setDataFormat(workbook.createDataFormat().getFormat("0.######"));
            warning = style(workbook, "FFF4CC", IndexedColors.DARK_RED.getIndex(), false, 10);
            warning.setWrapText(true);
            error = bordered(workbook, "FCE8E6", IndexedColors.DARK_RED.getIndex(), false);
            error.setWrapText(true);
            success = bordered(workbook, "E5F3E8", IndexedColors.DARK_GREEN.getIndex(), true);
            selected = bordered(workbook, "FFF1A8", IndexedColors.BLACK.getIndex(), true);
            finalScore = bordered(workbook, "DDECE2", IndexedColors.DARK_GREEN.getIndex(), true);
            finalScore.setDataFormat(workbook.createDataFormat().getFormat("0.######"));
        }

        private static CellStyle style(
            SXSSFWorkbook workbook,
            String fill,
            short fontColor,
            boolean bold,
            int fontSize
        ) {
            XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fill != null) {
                style.setFillForegroundColor(new XSSFColor(java.awt.Color.decode("#" + fill), null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            Font font = workbook.createFont();
            font.setColor(fontColor);
            font.setBold(bold);
            font.setFontHeightInPoints((short) fontSize);
            style.setFont(font);
            return style;
        }

        private static CellStyle bordered(SXSSFWorkbook workbook, String fill, short fontColor, boolean bold) {
            CellStyle style = style(workbook, fill, fontColor, bold, 10);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            return style;
        }
    }
}
