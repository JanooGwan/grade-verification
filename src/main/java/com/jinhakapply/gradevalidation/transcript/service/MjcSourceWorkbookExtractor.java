package com.jinhakapply.gradevalidation.transcript.service;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

@Component
class MjcSourceWorkbookExtractor {
    static final String SOURCE_FORMAT = "MJC_SOURCE_WORKBOOK_V1";

    ExtractedBundle extract(Path workbookPath, Path directory) {
        Map<SheetType, Path> targets = Map.of(
            SheetType.APPLICANTS, directory.resolve("01_applicants.csv"),
            SheetType.BASE_INFO, directory.resolve("04_base_info.csv"),
            SheetType.SUBJECTS, directory.resolve("05_subject_scores.csv")
        );
        Set<SheetType> found = new HashSet<>();
        try (OPCPackage pkg = OPCPackage.open(workbookPath.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            SharedStrings strings = reader.getSharedStringsTable();
            XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (iterator.hasNext()) {
                try (InputStream sheet = iterator.next()) {
                    SheetType type = SheetType.from(iterator.getSheetName());
                    if (type == null) continue;
                    if (!found.add(type)) {
                        throw new IllegalArgumentException(type.label + " 시트가 두 개 이상 있습니다.");
                    }
                    extractSheet(sheet, targets.get(type), styles, strings);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("명지전문대 통합 Excel을 읽지 못했습니다: " + exception.getMessage(), exception);
        }
        Set<SheetType> missing = new HashSet<>(Set.of(SheetType.values()));
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(missing.stream().map(type -> type.label).sorted().toList()
                + " 시트가 없습니다.");
        }
        return new ExtractedBundle(
            targets.get(SheetType.APPLICANTS), targets.get(SheetType.BASE_INFO), targets.get(SheetType.SUBJECTS)
        );
    }

    private void extractSheet(
        InputStream input,
        Path target,
        StylesTable styles,
        SharedStrings strings
    ) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            XMLReader xmlReader = XMLHelper.newXMLReader();
            xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                styles, null, strings, new CsvSheetHandler(writer), new DataFormatter(Locale.KOREA), false
            ));
            xmlReader.parse(new InputSource(input));
        }
    }

    record ExtractedBundle(Path applicantsFile, Path baseInfoFile, Path subjectScoreFile) {}

    private enum SheetType {
        APPLICANTS("01_지원자정보"),
        BASE_INFO("04_학생부기본정보"),
        SUBJECTS("05_교과학습발달상황_SubjectScore");

        private final String label;

        SheetType(String label) {
            this.label = label;
        }

        private static SheetType from(String sheetName) {
            String normalized = sheetName == null ? "" : sheetName.trim();
            for (SheetType type : values()) {
                if (normalized.equals(type.label) || normalized.startsWith(type.label + "_")) return type;
            }
            return null;
        }
    }

    private static final class CsvSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final BufferedWriter writer;
        private final Map<Integer, String> values = new HashMap<>();
        private int columnCount = -1;

        private CsvSheetHandler(BufferedWriter writer) {
            this.writer = writer;
        }

        @Override public void startRow(int rowNum) {
            values.clear();
        }

        @Override public void endRow(int rowNum) {
            if (values.isEmpty()) return;
            if (columnCount < 0) {
                columnCount = values.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            }
            try {
                for (int index = 0; index < columnCount; index++) {
                    if (index > 0) writer.write(',');
                    writer.write(escape(values.getOrDefault(index, "")));
                }
                writer.newLine();
            } catch (Exception exception) {
                throw new IllegalArgumentException("Excel 시트를 임시 CSV로 변환하지 못했습니다.", exception);
            }
        }

        @Override public void cell(String reference, String formattedValue, XSSFComment comment) {
            values.put(columnIndex(reference), formattedValue == null ? "" : formattedValue);
        }

        private String escape(String value) {
            if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
                return value;
            }
            return '"' + value.replace("\"", "\"\"") + '"';
        }

        private int columnIndex(String reference) {
            int value = 0;
            for (int index = 0; index < reference.length() && Character.isLetter(reference.charAt(index)); index++) {
                value = value * 26 + Character.toUpperCase(reference.charAt(index)) - 'A' + 1;
            }
            return value - 1;
        }
    }
}
