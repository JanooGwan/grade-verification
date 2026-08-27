package com.jinhakapply.gradevalidation.evaluation.service;

import static java.math.RoundingMode.HALF_UP;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class PdfRuleHeuristicExtractor {

    private static final List<String> NO_YEAR_WEIGHT_TERMS = List.of(
        "학년별 차등 없이", "학년별 반영비율 없음", "학년별 가중치 없음"
    );

    private static final Pattern SELECTION_COUNT = Pattern.compile(
        "(?:상위|우수)\\s*(\\d+)\\s*(?:개\\s*)?(교과영역|교과|과목|학기)"
    );
    private static final Pattern EXPLICIT_YEAR_WEIGHTS = Pattern.compile(
        "1학년\\s*(\\d+(?:\\.\\d+)?)\\s*%?.{0,180}?"
            + "2학년\\s*(\\d+(?:\\.\\d+)?)\\s*%?.{0,180}?"
            + "3학년\\s*(\\d+(?:\\.\\d+)?)\\s*%?"
    );
    private static final Pattern SCORE_ROW = Pattern.compile(
        "(?:배점|환산점수)\\s+((?:\\d+(?:\\.\\d+)?\\s+){4,8}\\d+(?:\\.\\d+)?)"
    );
    private static final Pattern ACHIEVEMENT_ROW = Pattern.compile(
        "성취도\\s*A\\s*B\\s*C.{0,180}?(?:배점|환산점수)\\s+"
            + "(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)"
    );

    RuleExtractionAnalysis extract(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() > 500) {
                throw new IOException("500페이지를 초과하는 PDF는 처리할 수 없습니다.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(normalize(stripper.getText(document)));
            }
            return extractFromPages(pages);
        }
    }

    RuleExtractionAnalysis extractFromPages(List<String> pages) {
        List<PageText> pageTexts = new ArrayList<>();
        int textPageCount = 0;
        for (int index = 0; index < pages.size(); index++) {
            String text = normalize(pages.get(index));
            if (text.length() >= 80) textPageCount++;
            pageTexts.add(new PageText(index + 1, text, relevance(text)));
        }
        List<PageText> relevant = pageTexts.stream()
            .filter(page -> page.relevance() >= 4)
            .sorted(Comparator.comparingInt(PageText::relevance).reversed())
            .limit(20)
            .toList();
        String combined = relevant.stream().map(PageText::text).collect(java.util.stream.Collectors.joining(" "));
        List<RuleExtractionAnalysis.Evidence> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        InferredSelection selection = inferSelection(relevant, combined, evidence);
        List<BigDecimal> gradeWeights = inferYearWeights(relevant, combined, evidence, warnings);
        Boolean applyGradeWeights = inferGradeWeightApplication(combined, gradeWeights);
        List<BigDecimal> gradeScores = inferGradeScores(relevant, evidence, warnings);
        List<BigDecimal> achievementScores = inferAchievementScores(relevant, evidence, warnings);
        List<SubjectCategory> subjectCategories = inferSubjects(relevant, evidence);
        Boolean includeThirdYearSecondSemester = inferSemesterScope(relevant, combined, evidence, warnings);
        RoundingMode roundingMode = inferRounding(relevant, combined, evidence, warnings);

        if (pages.isEmpty() || textPageCount < Math.max(1, pages.size() / 5)) {
            warnings.add("텍스트가 추출되는 페이지가 적습니다. 이미지 기반 PDF라면 OCR 후 다시 업로드해야 합니다.");
        }
        if (relevant.isEmpty()) {
            warnings.add("학생부 반영 방식과 관련된 페이지를 찾지 못했습니다.");
        }

        List<String> missingFields = new ArrayList<>();
        if (selection.strategy() == null) missingFields.add("과목 선택 방식");
        if (gradeWeights.isEmpty()) missingFields.add("학년별 반영 비율");
        if (gradeScores.isEmpty()) missingFields.add("석차등급 환산표");
        if (subjectCategories.isEmpty()) missingFields.add("반영 교과");
        if (includeThirdYearSecondSemester == null) missingFields.add("3학년 2학기 반영 여부");
        if (roundingMode == null) missingFields.add("반올림·절사 기준");

        Set<Integer> sourcePageNumbers = new java.util.TreeSet<>();
        evidence.forEach(item -> sourcePageNumbers.add(item.pageNumber()));
        String sourcePages = sourcePageNumbers.isEmpty() ? null : sourcePageNumbers.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(", "));

        BigDecimal confidence = calculateConfidence(evidence, missingFields.size());
        if (confidence.compareTo(new BigDecimal("0.75")) < 0) {
            warnings.add("자동 추출 신뢰도가 낮거나 미확정 필드가 있습니다. 원문 대조 후 초안을 저장해 주세요.");
        }

        return new RuleExtractionAnalysis(
            pages.size(),
            textPageCount,
            selection.strategy(),
            selection.count(),
            gradeWeights,
            applyGradeWeights,
            gradeScores,
            achievementScores,
            subjectCategories,
            includeThirdYearSecondSemester,
            roundingMode,
            sourcePages,
            confidence,
            List.copyOf(missingFields),
            List.copyOf(new LinkedHashSet<>(warnings)),
            List.copyOf(evidence)
        );
    }

    private InferredSelection inferSelection(
        List<PageText> pages,
        String combined,
        List<RuleExtractionAnalysis.Evidence> evidence
    ) {
        if (combined.contains("학년별 우수 학기") || combined.contains("학년별 최우수 학기")) {
            addEvidence(evidence, pages, "selectionStrategy", "학년별", "0.90");
            return new InferredSelection(SelectionStrategy.BEST_SEMESTER_PER_GRADE, 0);
        }
        if (combined.contains("사회") && combined.contains("과학") && combined.contains("이수단위")
            && (combined.contains("교과별 상위") || combined.contains("상위 4과목"))) {
            addEvidence(evidence, pages, "selectionStrategy", "이수단위", "0.82");
            return new InferredSelection(SelectionStrategy.CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N, 4);
        }
        Matcher matcher = SELECTION_COUNT.matcher(combined);
        if (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            SelectionStrategy strategy;
            if (unit.contains("학기")) strategy = SelectionStrategy.TOP_N_SEMESTERS;
            else if (unit.contains("교과")) strategy = SelectionStrategy.TOP_N_SUBJECTS;
            else if (combined.contains("교과별")) strategy = SelectionStrategy.TOP_N_COURSES_PER_SUBJECT;
            else strategy = SelectionStrategy.TOP_N_COURSES;
            addEvidence(evidence, pages, "selectionStrategy", matcher.group(), "0.86");
            return new InferredSelection(strategy, count);
        }
        if (combined.contains("이수한 모든 과목") || combined.contains("전 과목 반영")
            || combined.contains("영역 전 과목")) {
            addEvidence(evidence, pages, "selectionStrategy", "전 과목", "0.88");
            return new InferredSelection(SelectionStrategy.ALL_COURSES, 0);
        }
        return new InferredSelection(null, null);
    }

    private List<BigDecimal> inferYearWeights(
        List<PageText> pages,
        String combined,
        List<RuleExtractionAnalysis.Evidence> evidence,
        List<String> warnings
    ) {
        if (containsAny(combined, NO_YEAR_WEIGHT_TERMS)) {
            addEvidence(evidence, pages, "gradeWeights", "학년별", "0.88");
            return decimals("1", "1", "1");
        }
        Set<List<BigDecimal>> candidates = new LinkedHashSet<>();
        for (PageText page : pages) {
            Matcher matcher = EXPLICIT_YEAR_WEIGHTS.matcher(page.text());
            while (matcher.find()) {
                List<BigDecimal> values = decimals(matcher.group(1), matcher.group(2), matcher.group(3));
                BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                if (sum.compareTo(new BigDecimal("100")) == 0) {
                    candidates.add(values);
                    evidence.add(evidence("gradeWeights", page, matcher.group(), "0.84"));
                }
            }
        }
        if (candidates.size() == 1) return candidates.iterator().next();
        if (candidates.size() > 1) warnings.add("서로 다른 학년별 반영 비율이 발견되어 자동 선택하지 않았습니다.");
        return List.of();
    }

    private Boolean inferGradeWeightApplication(String combined, List<BigDecimal> gradeWeights) {
        if (containsAny(combined, NO_YEAR_WEIGHT_TERMS)) return false;
        return gradeWeights.isEmpty() ? null : true;
    }

    private boolean containsAny(String text, List<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }

    private List<BigDecimal> inferGradeScores(
        List<PageText> pages,
        List<RuleExtractionAnalysis.Evidence> evidence,
        List<String> warnings
    ) {
        Set<List<BigDecimal>> fullTables = new LinkedHashSet<>();
        boolean foundPartialTable = false;
        for (PageText page : pages) {
            if (!page.text().contains("석차등급")) continue;
            Matcher matcher = SCORE_ROW.matcher(page.text());
            while (matcher.find()) {
                List<BigDecimal> scores = java.util.Arrays.stream(matcher.group(1).trim().split("\\s+"))
                    .map(BigDecimal::new)
                    .toList();
                if (scores.size() == 9) {
                    fullTables.add(scores);
                    evidence.add(evidence("gradeScores", page, matcher.group(), "0.82"));
                } else {
                    foundPartialTable = true;
                }
            }
        }
        if (fullTables.size() == 1 && !foundPartialTable) return fullTables.iterator().next();
        if (fullTables.size() > 1 || (foundPartialTable && !fullTables.isEmpty())) {
            warnings.add("모집단위별로 다른 석차등급표가 발견되어 자동 선택하지 않았습니다.");
        }
        return List.of();
    }

    private List<BigDecimal> inferAchievementScores(
        List<PageText> pages,
        List<RuleExtractionAnalysis.Evidence> evidence,
        List<String> warnings
    ) {
        Set<List<BigDecimal>> candidates = new LinkedHashSet<>();
        for (PageText page : pages) {
            Matcher matcher = ACHIEVEMENT_ROW.matcher(page.text());
            while (matcher.find()) {
                candidates.add(decimals(matcher.group(1), matcher.group(2), matcher.group(3)));
                evidence.add(evidence("achievementScores", page, matcher.group(), "0.86"));
            }
        }
        if (candidates.size() == 1) return candidates.iterator().next();
        if (candidates.size() > 1) warnings.add("서로 다른 성취도 환산표가 발견되어 자동 선택하지 않았습니다.");
        return List.of();
    }

    private List<SubjectCategory> inferSubjects(
        List<PageText> pages,
        List<RuleExtractionAnalysis.Evidence> evidence
    ) {
        EnumSet<SubjectCategory> categories = EnumSet.noneOf(SubjectCategory.class);
        PageText bestPage = null;
        for (PageText page : pages) {
            if (!(page.text().contains("반영 교과") || page.text().contains("교과영역"))) continue;
            if (page.text().contains("국어")) categories.add(SubjectCategory.KOREAN);
            if (page.text().contains("수학")) categories.add(SubjectCategory.MATH);
            if (page.text().contains("영어")) categories.add(SubjectCategory.ENGLISH);
            if (page.text().contains("사회") || page.text().contains("역사") || page.text().contains("도덕"))
                categories.add(SubjectCategory.SOCIAL);
            if (page.text().contains("과학")) categories.add(SubjectCategory.SCIENCE);
            if (bestPage == null || page.relevance() > bestPage.relevance()) bestPage = page;
        }
        if (!categories.isEmpty() && bestPage != null) {
            evidence.add(evidence("subjectCategories", bestPage, "반영 교과", "0.72"));
        }
        return List.copyOf(categories);
    }

    private Boolean inferSemesterScope(
        List<PageText> pages,
        String combined,
        List<RuleExtractionAnalysis.Evidence> evidence,
        List<String> warnings
    ) {
        boolean firstSemester = combined.contains("3학년 1학기까지") || combined.contains("3학년 1학기 까지");
        boolean secondSemester = combined.contains("3학년 2학기까지") || combined.contains("3학년 2학기 까지");
        if (firstSemester && secondSemester) {
            warnings.add("졸업연도 등 조건에 따라 3학년 반영 학기가 달라 자동 선택하지 않았습니다.");
            return null;
        }
        if (firstSemester) {
            addEvidence(evidence, pages, "includeThirdYearSecondSemester", "3학년 1학기", "0.91");
            return false;
        }
        if (secondSemester) {
            addEvidence(evidence, pages, "includeThirdYearSecondSemester", "3학년 2학기", "0.91");
            return true;
        }
        return null;
    }

    private RoundingMode inferRounding(
        List<PageText> pages,
        String combined,
        List<RuleExtractionAnalysis.Evidence> evidence,
        List<String> warnings
    ) {
        boolean down = combined.contains("절사") || combined.contains("버림");
        boolean halfUp = combined.contains("반올림");
        if (down && halfUp) {
            warnings.add("계산 단계별로 절사와 반올림이 함께 사용되어 자동 선택하지 않았습니다.");
            return null;
        }
        if (down) {
            addEvidence(evidence, pages, "roundingMode", combined.contains("절사") ? "절사" : "버림", "0.78");
            return RoundingMode.DOWN;
        }
        if (halfUp) {
            addEvidence(evidence, pages, "roundingMode", "반올림", "0.78");
            return HALF_UP;
        }
        return null;
    }

    private void addEvidence(
        List<RuleExtractionAnalysis.Evidence> evidence,
        List<PageText> pages,
        String fieldKey,
        String keyword,
        String confidence
    ) {
        pages.stream().filter(page -> page.text().contains(keyword)).findFirst()
            .ifPresent(page -> evidence.add(evidence(fieldKey, page, keyword, confidence)));
    }

    private RuleExtractionAnalysis.Evidence evidence(
        String fieldKey,
        PageText page,
        String keyword,
        String confidence
    ) {
        return new RuleExtractionAnalysis.Evidence(
            fieldKey,
            page.pageNumber(),
            excerpt(page.text(), keyword),
            new BigDecimal(confidence)
        );
    }

    private String excerpt(String text, String keyword) {
        int index = Math.max(0, text.indexOf(keyword));
        int start = Math.max(0, index - 180);
        int end = Math.min(text.length(), index + Math.max(keyword.length(), 1) + 320);
        return text.substring(start, end).trim();
    }

    private BigDecimal calculateConfidence(List<RuleExtractionAnalysis.Evidence> evidence, int missingCount) {
        if (evidence.isEmpty()) return BigDecimal.ZERO.setScale(4);
        BigDecimal average = evidence.stream().map(RuleExtractionAnalysis.Evidence::confidence)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(evidence.size()), 6, HALF_UP);
        BigDecimal coverage = BigDecimal.valueOf(6 - missingCount)
            .divide(BigDecimal.valueOf(6), 6, HALF_UP);
        return average.multiply(new BigDecimal("0.5").add(coverage.multiply(new BigDecimal("0.5"))))
            .min(new BigDecimal("0.95"))
            .setScale(4, HALF_UP);
    }

    private int relevance(String text) {
        int score = 0;
        score += contains(text, "학생부 반영", 5);
        score += contains(text, "교과성적", 4);
        score += contains(text, "석차등급", 4);
        score += contains(text, "반영 교과", 3);
        score += contains(text, "성취도", 2);
        score += contains(text, "이수단위", 2);
        score += contains(text, "학년별", 2);
        score += contains(text, "환산점수", 2);
        return score;
    }

    private int contains(String text, String keyword, int score) {
        return text.contains(keyword) ? score : 0;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replace('\u00A0', ' ')
            .replaceAll("[\\p{Z}\\s]+", " ")
            .trim();
    }

    private static List<BigDecimal> decimals(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private record PageText(int pageNumber, String text, int relevance) {
    }

    private record InferredSelection(SelectionStrategy strategy, Integer count) {
    }
}
