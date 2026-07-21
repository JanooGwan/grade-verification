package com.jinhakapply.gradevalidation.evaluation.service;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.EVALUATION_RULE_NOT_FOUND;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.DUPLICATE_EVALUATION_RULE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_EVALUATION_RULE;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_EVALUATION_RULE_STATUS;
import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.UNIVERSITY_NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jinhakapply.gradevalidation.evaluation.domain.AchievementConversion;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRule;
import com.jinhakapply.gradevalidation.evaluation.domain.EvaluationRuleStatus;
import com.jinhakapply.gradevalidation.evaluation.domain.ScoreAggregation;
import com.jinhakapply.gradevalidation.evaluation.domain.SelectionStrategy;
import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.dto.BulkCreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.CreateEvaluationRuleRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleActionRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.EvaluationRuleResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CourseCalculation;
import com.jinhakapply.gradevalidation.evaluation.dto.GradeVerificationResponse.CalculationSummary;
import com.jinhakapply.gradevalidation.evaluation.dto.VerifyGradeRequest;
import com.jinhakapply.gradevalidation.evaluation.repository.EvaluationRuleRepository;
import com.jinhakapply.gradevalidation.global.exception.CustomException;
import com.jinhakapply.gradevalidation.university.domain.University;
import com.jinhakapply.gradevalidation.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final EvaluationRuleRepository ruleRepository;
    private final UniversityRepository universityRepository;

    @Transactional
    public EvaluationRuleResponse createRule(CreateEvaluationRuleRequest request) {
        validateRule(request);
        if (ruleRepository.existsByUniversityIdAndAdmissionYearAndAdmissionTypeAndRecruitmentUnitAndVersion(
            request.universityId(),
            request.admissionYear(),
            request.admissionType().trim(),
            request.recruitmentUnit().trim(),
            request.version()
        )) {
            throw CustomException.of(DUPLICATE_EVALUATION_RULE);
        }
        University university = universityRepository.findById(request.universityId())
            .orElseThrow(() -> CustomException.of(UNIVERSITY_NOT_FOUND, request.universityId().toString()));
        EvaluationRule rule = EvaluationRule.create(university, request.name(), request.admissionYear(),
            request.admissionType(), request.recruitmentUnit(), request.version(), request.gradeWeights(),
            request.subjectWeights(), request.gradeScores(), request.selectionStrategy(), request.selectionCount(),
            request.achievementSelectionCount(), request.minimumCourseCount(), request.scoreAggregation(),
            request.achievementConversion(),
            request.includeThirdYearSecondSemester(), request.includeThirdYearSecondSemesterForGraduates(),
            request.includeProfessionalCourses(), request.applyGradeWeights(), request.normalizeGradeWeights(),
            request.intermediateScale(),
            request.intermediateRounding(), request.finalScale(), request.finalRounding(), request.scoreMultiplier(),
            request.achievementGrades(), request.achievementScores(), request.subjectPriorities(),
            request.sourceDocument(), request.sourcePages(), request.interpretationNote(), request.changeSummary());
        return EvaluationRuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public List<EvaluationRuleResponse> createDraftRules(BulkCreateEvaluationRuleRequest request) {
        return request.rules().stream().map(this::createRule).toList();
    }

    public List<EvaluationRuleResponse> findRules() {
        return ruleRepository.findAllByStatusOrderByAdmissionYearDescNameAsc(EvaluationRuleStatus.PUBLISHED).stream()
            .map(EvaluationRuleResponse::from)
            .toList();
    }

    public List<EvaluationRuleResponse> findAdminRules(EvaluationRuleStatus status) {
        List<EvaluationRule> rules = status == null
            ? ruleRepository.findAllByOrderByAdmissionYearDescNameAsc()
            : ruleRepository.findAllByStatusOrderByAdmissionYearDescNameAsc(status);
        return rules.stream().map(EvaluationRuleResponse::from).toList();
    }

    @Transactional
    public EvaluationRuleResponse reviewRule(Long ruleId, EvaluationRuleActionRequest request) {
        EvaluationRule rule = findRule(ruleId);
        requireStatus(rule, EvaluationRuleStatus.DRAFT);
        rule.markVerified(request.actor(), request.note());
        return EvaluationRuleResponse.from(rule);
    }

    @Transactional
    public EvaluationRuleResponse publishRule(Long ruleId, EvaluationRuleActionRequest request) {
        EvaluationRule rule = findRule(ruleId);
        requireStatus(rule, EvaluationRuleStatus.VERIFIED);
        universityRepository.findByIdForUpdate(rule.getUniversity().getId());
        ruleRepository.findAllByUniversityIdAndAdmissionYearAndAdmissionTypeAndRecruitmentUnitAndStatus(
            rule.getUniversity().getId(),
            rule.getAdmissionYear(),
            rule.getAdmissionType(),
            rule.getRecruitmentUnit(),
            EvaluationRuleStatus.PUBLISHED
        ).forEach(previous -> previous.retire(request.actor(), "새 버전 게시로 자동 폐기"));
        rule.publish(request.actor(), request.note());
        return EvaluationRuleResponse.from(rule);
    }

    @Transactional
    public EvaluationRuleResponse retireRule(Long ruleId, EvaluationRuleActionRequest request) {
        EvaluationRule rule = findRule(ruleId);
        if (rule.getStatus() == EvaluationRuleStatus.RETIRED) {
            throw CustomException.of(INVALID_EVALUATION_RULE_STATUS, "이미 폐기된 규칙입니다.");
        }
        rule.retire(request.actor(), request.note());
        return EvaluationRuleResponse.from(rule);
    }

    public GradeVerificationResponse verify(VerifyGradeRequest request) {
        EvaluationRule rule = findRule(request.ruleId());
        if (!rule.isPublished()) {
            throw CustomException.of(INVALID_EVALUATION_RULE_STATUS, "게시된 규칙만 성적 계산에 사용할 수 있습니다.");
        }
        List<Candidate> candidates = prepareCandidates(rule, request.courses(), request.graduated());
        CourseSelection selection = selectCourses(rule, candidates.stream().filter(Candidate::eligible).toList());
        Set<Integer> selectedIndexes = selection.indexes();
        if (selectedIndexes.size() < rule.getMinimumCourseCount()) {
            throw CustomException.of(INVALID_EVALUATION_RULE,
                "반영 가능한 교과성적이 최소 " + rule.getMinimumCourseCount() + "과목 이상이어야 합니다.");
        }
        Map<Integer, BigDecimal> yearWeightDenominators = calculateYearWeightDenominators(rule, candidates, selection);

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalConvertedScore = BigDecimal.ZERO;
        BigDecimal totalGrade = BigDecimal.ZERO;
        BigDecimal totalGradeTimesCredits = BigDecimal.ZERO;
        BigDecimal totalConvertedScoreTimesCredits = BigDecimal.ZERO;
        BigDecimal totalIncludedCredits = BigDecimal.ZERO;
        List<CourseCalculation> calculations = new ArrayList<>();
        int included = 0;

        for (Candidate candidate : candidates) {
            VerifyGradeRequest.CourseGrade course = candidate.course();
            BigDecimal gradeWeight = rule.isApplyGradeWeights()
                ? rule.gradeWeight(course.schoolYear()) : BigDecimal.ONE;
            SubjectCategory appliedSubjectCategory = selection.appliedSubjectCategory(candidate);
            BigDecimal subjectWeight = rule.subjectWeight(appliedSubjectCategory);
            boolean selected = candidate.eligible() && selectedIndexes.contains(candidate.index());
            BigDecimal appliedWeight = course.credits().multiply(gradeWeight).multiply(subjectWeight);
            if (selected && rule.isApplyGradeWeights() && rule.isNormalizeGradeWeights()) {
                appliedWeight = course.credits().multiply(subjectWeight).multiply(gradeWeight)
                    .divide(yearWeightDenominators.get(course.schoolYear()), 12, RoundingMode.HALF_UP);
            }
            String exclusionReason = candidate.exclusionReason();
            if (candidate.eligible() && !selected) exclusionReason = "모집요강의 과목 선택 기준에 따라 제외되었습니다.";
            BigDecimal weightedScore = BigDecimal.ZERO;
            if (selected) {
                included++;
                totalWeight = totalWeight.add(appliedWeight);
                totalIncludedCredits = totalIncludedCredits.add(course.credits());
                totalGradeTimesCredits = totalGradeTimesCredits.add(
                    candidate.effectiveGrade().multiply(course.credits())
                );
                totalConvertedScoreTimesCredits = totalConvertedScoreTimesCredits.add(
                    candidate.convertedScore().multiply(course.credits())
                );
                totalGrade = totalGrade.add(candidate.effectiveGrade().multiply(appliedWeight));
                weightedScore = candidate.convertedScore().multiply(appliedWeight);
                totalConvertedScore = totalConvertedScore.add(weightedScore);
            }
            calculations.add(new CourseCalculation(course.courseName().trim(), course.schoolYear(), course.semester(),
                course.subjectCategory(), appliedSubjectCategory, course.grade(), course.achievement(), candidate.effectiveGrade(),
                candidate.convertedScore(), gradeWeight, subjectWeight, course.credits(), appliedWeight,
                weightedScore, selected, exclusionReason));
        }

        if (totalWeight.signum() == 0) {
            throw CustomException.of(INVALID_EVALUATION_RULE, "반영 가능한 과목이 없습니다.");
        }

        BigDecimal averageGrade = totalGrade.divide(totalWeight, rule.getIntermediateScale(), rule.getIntermediateRounding());
        BigDecimal baseScore = rule.getScoreAggregation() == ScoreAggregation.AVERAGE_GRADE_THEN_SCORE
            ? interpolateGradeScore(rule, averageGrade)
            : totalConvertedScore.divide(totalWeight, rule.getIntermediateScale(), rule.getIntermediateRounding());
        BigDecimal finalScore = baseScore.multiply(rule.getScoreMultiplier())
            .setScale(rule.getFinalScale(), rule.getFinalRounding());
        BigDecimal scoreBeforeFinalRounding = baseScore.multiply(rule.getScoreMultiplier());
        CalculationSummary calculationSummary = new CalculationSummary(
            calculationFormula(rule.getScoreAggregation(), rule.isApplyGradeWeights()),
            totalGradeTimesCredits, totalConvertedScoreTimesCredits,
            totalGrade, totalConvertedScore, totalWeight, totalIncludedCredits, averageGrade, baseScore,
            rule.getScoreMultiplier(), scoreBeforeFinalRounding,
            rule.getIntermediateScale(), rule.getIntermediateRounding(), rule.getFinalScale(), rule.getFinalRounding(),
            Map.copyOf(yearWeightDenominators)
        );

        List<String> warnings = buildWarnings(rule, request.courses(), candidates, included);
        return new GradeVerificationResponse(rule.getId(), rule.getName(), rule.getVersion(), rule.getUniversity().getName(),
            rule.getAdmissionType(), rule.getRecruitmentUnit(), finalScore, baseScore, averageGrade,
            rule.getSelectionStrategy(), rule.getScoreAggregation(), rule.getSourceDocument(), rule.getSourcePages(),
            included, calculations.size() - included, calculationSummary, calculations, warnings);
    }

    private String calculationFormula(ScoreAggregation aggregation, boolean applyGradeWeights) {
        String weight = applyGradeWeights ? "적용가중치" : "이수단위 × 교과가중치";
        return aggregation == ScoreAggregation.AVERAGE_GRADE_THEN_SCORE
            ? "Σ(유효등급 × " + weight + ") ÷ Σ(" + weight + ") → 등급 환산표 × 점수 배율"
            : "Σ(과목 환산점수 × " + weight + ") ÷ Σ(" + weight + ") × 점수 배율";
    }

    private List<Candidate> prepareCandidates(EvaluationRule rule, List<VerifyGradeRequest.CourseGrade> courses,
        boolean graduated) {
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < courses.size(); index++) {
            VerifyGradeRequest.CourseGrade course = courses.get(index);
            String exclusionReason = null;
            if (rule.isApplyGradeWeights() && rule.gradeWeight(course.schoolYear()).signum() == 0)
                exclusionReason = "학년 반영 비율이 0입니다.";
            else if (!hasEligibleSubjectWeight(rule, course)) exclusionReason = "반영하지 않는 교과입니다.";
            else if (course.schoolYear() == 3 && course.semester() == 2
                && !includesThirdYearSecondSemester(rule, graduated))
                exclusionReason = "3학년 2학기는 이 규칙의 반영 범위가 아닙니다.";
            else if (course.professionalCourse() && !rule.isIncludeProfessionalCourses())
                exclusionReason = "전문교과는 이 규칙에서 제외됩니다.";

            BigDecimal effectiveGrade = exclusionReason == null ? resolveEffectiveGrade(rule, course) : null;
            if (exclusionReason == null && effectiveGrade == null) exclusionReason = "등급 또는 환산 가능한 성취도 정보가 없습니다.";
            BigDecimal convertedScore = effectiveGrade == null ? null : resolveConvertedScore(rule, course, effectiveGrade);
            candidates.add(new Candidate(index, course, effectiveGrade, convertedScore, exclusionReason));
        }
        return candidates;
    }

    private boolean includesThirdYearSecondSemester(EvaluationRule rule, boolean graduated) {
        return rule.isIncludeThirdYearSecondSemester()
            || (graduated && rule.isIncludeThirdYearSecondSemesterForGraduates());
    }

    private boolean hasEligibleSubjectWeight(EvaluationRule rule, VerifyGradeRequest.CourseGrade course) {
        if (!isKoreanHistory(course)) return rule.subjectWeight(course.subjectCategory()).signum() > 0;
        return switch (rule.getSelectionStrategy()) {
            case CORE_SCIENCE_TOP_N -> rule.subjectWeight(SubjectCategory.SCIENCE).signum() > 0;
            case CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N -> rule.subjectWeight(SubjectCategory.SOCIAL).signum() > 0
                || rule.subjectWeight(SubjectCategory.SCIENCE).signum() > 0;
            default -> rule.subjectWeight(course.subjectCategory()).signum() > 0;
        };
    }

    private Map<Integer, BigDecimal> calculateYearWeightDenominators(EvaluationRule rule, List<Candidate> candidates,
        CourseSelection selection) {
        if (!rule.isApplyGradeWeights() || !rule.isNormalizeGradeWeights()) return Map.of();
        Map<Integer, BigDecimal> denominators = new HashMap<>();
        candidates.stream().filter(candidate -> selection.indexes().contains(candidate.index())).forEach(candidate -> {
            VerifyGradeRequest.CourseGrade course = candidate.course();
            BigDecimal weight = course.credits().multiply(rule.subjectWeight(selection.appliedSubjectCategory(candidate)));
            denominators.merge(course.schoolYear(), weight, BigDecimal::add);
        });
        return denominators;
    }

    private BigDecimal resolveEffectiveGrade(EvaluationRule rule, VerifyGradeRequest.CourseGrade course) {
        if (course.grade() != null) return BigDecimal.valueOf(course.grade());
        if (course.achievement() == null || rule.getAchievementConversion() == AchievementConversion.EXCLUDE) return null;
        if (rule.getAchievementConversion() == AchievementConversion.Z_SCORE
            && course.rawScore() != null && course.meanScore() != null && course.standardDeviation() != null) {
            return BigDecimal.valueOf(gradeFromZScore(course.rawScore(), course.meanScore(), course.standardDeviation()));
        }
        return rule.getAchievementGrades().get(course.achievement());
    }

    private BigDecimal resolveConvertedScore(EvaluationRule rule, VerifyGradeRequest.CourseGrade course, BigDecimal effectiveGrade) {
        if (course.grade() == null && course.achievement() != null
            && rule.getAchievementConversion() == AchievementConversion.DIRECT_TABLE) {
            BigDecimal directScore = rule.getAchievementScores().get(course.achievement());
            if (directScore != null) return directScore;
        }
        return interpolateGradeScore(rule, effectiveGrade);
    }

    private BigDecimal interpolateGradeScore(EvaluationRule rule, BigDecimal grade) {
        BigDecimal bounded = grade.max(BigDecimal.ONE).min(BigDecimal.valueOf(9));
        int lower = bounded.setScale(0, RoundingMode.FLOOR).intValue();
        int upper = bounded.setScale(0, RoundingMode.CEILING).intValue();
        BigDecimal lowerScore = rule.getGradeScores().get(lower);
        if (lower == upper) return lowerScore;
        BigDecimal upperScore = rule.getGradeScores().get(upper);
        BigDecimal fraction = bounded.subtract(BigDecimal.valueOf(lower));
        return lowerScore.add(upperScore.subtract(lowerScore).multiply(fraction));
    }

    private CourseSelection selectCourses(EvaluationRule rule, List<Candidate> eligible) {
        if (eligible.isEmpty()) return CourseSelection.empty();
        return switch (rule.getSelectionStrategy()) {
            case ALL_COURSES -> CourseSelection.of(indexesOf(eligible));
            case TOP_N_COURSES -> CourseSelection.of(indexesOf(eligible.stream().sorted(courseComparator()).limit(rule.getSelectionCount()).toList()));
            case TOP_N_COURSES_PER_SUBJECT -> CourseSelection.of(selectTopCoursesPerSubject(rule, eligible));
            case CORE_SCIENCE_TOP_N -> selectCoreScienceSubjects(rule, eligible);
            case CORE_PLUS_BEST_CREDIT_OPTIONAL_TOP_N -> selectCoreAndBestOptionalSubject(rule, eligible);
            case TOP_N_SEMESTERS -> CourseSelection.of(selectTopGroups(rule, eligible,
                candidate -> candidate.course().schoolYear() + "-" + candidate.course().semester(), rule.getSelectionCount(), null));
            case TOP_N_SUBJECTS -> CourseSelection.of(selectTopGroups(rule, eligible, candidate -> candidate.course().subjectCategory(),
                rule.getSelectionCount(), rule.getSubjectPriorities()));
            case BEST_SEMESTER_PER_GRADE -> CourseSelection.of(selectBestSemesterPerGrade(eligible));
        };
    }

    private Set<Integer> selectTopCoursesPerSubject(EvaluationRule rule, List<Candidate> eligible) {
        Set<Integer> selected = new LinkedHashSet<>();
        Map<SubjectCategory, List<Candidate>> bySubject = eligible.stream()
            .collect(Collectors.groupingBy(candidate -> candidate.course().subjectCategory(), () -> new EnumMap<>(SubjectCategory.class), Collectors.toList()));
        for (List<Candidate> subjectCourses : bySubject.values()) {
            subjectCourses.stream().filter(candidate -> !candidate.course().careerSubject()).sorted(courseComparator())
                .limit(rule.getSelectionCount()).map(Candidate::index).forEach(selected::add);
            subjectCourses.stream().filter(candidate -> candidate.course().careerSubject()).sorted(courseComparator())
                .limit(rule.getAchievementSelectionCount()).map(Candidate::index).forEach(selected::add);
        }
        return selected;
    }

    private CourseSelection selectCoreScienceSubjects(EvaluationRule rule, List<Candidate> eligible) {
        Set<SubjectCategory> selectedSubjects = Set.of(
            SubjectCategory.KOREAN, SubjectCategory.MATH, SubjectCategory.ENGLISH, SubjectCategory.SCIENCE);
        Map<Integer, SubjectCategory> appliedSubjects = new HashMap<>();
        eligible.stream().filter(this::isKoreanHistory)
            .forEach(candidate -> appliedSubjects.put(candidate.index(), SubjectCategory.SCIENCE));
        List<Candidate> included = eligible.stream()
            .filter(candidate -> selectedSubjects.contains(appliedSubjects.getOrDefault(
                candidate.index(), candidate.course().subjectCategory())))
            .toList();
        Set<Integer> selected = selectTopCoursesPerAppliedSubject(rule, included, appliedSubjects, true);
        return new CourseSelection(selected, appliedSubjects);
    }

    private CourseSelection selectCoreAndBestOptionalSubject(EvaluationRule rule, List<Candidate> eligible) {
        List<SubjectCategory> optionalSubjects = List.of(SubjectCategory.SOCIAL, SubjectCategory.SCIENCE);
        SubjectCategory optional = optionalSubjects.stream().max(Comparator
            .comparing((SubjectCategory category) -> totalCreditsExcludingKoreanHistory(eligible, category))
            .thenComparing(category -> -rule.getSubjectPriorities().getOrDefault(category, Integer.MAX_VALUE)))
            .orElse(SubjectCategory.SOCIAL);
        Set<SubjectCategory> selectedSubjects = Set.of(
            SubjectCategory.KOREAN, SubjectCategory.MATH, SubjectCategory.ENGLISH, optional);
        Map<Integer, SubjectCategory> appliedSubjects = new HashMap<>();
        eligible.stream().filter(this::isKoreanHistory)
            .forEach(candidate -> appliedSubjects.put(candidate.index(), optional));
        List<Candidate> included = eligible.stream()
            .filter(candidate -> selectedSubjects.contains(appliedSubjects.getOrDefault(
                candidate.index(), candidate.course().subjectCategory())))
            .toList();
        Set<Integer> selected = selectTopCoursesPerAppliedSubject(rule, included, appliedSubjects, true);
        return new CourseSelection(selected, appliedSubjects);
    }

    private BigDecimal totalCreditsExcludingKoreanHistory(List<Candidate> candidates, SubjectCategory category) {
        return candidates.stream().filter(candidate -> candidate.course().subjectCategory() == category)
            .filter(candidate -> !isKoreanHistory(candidate))
            .map(candidate -> candidate.course().credits()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Set<Integer> selectTopCoursesPerAppliedSubject(EvaluationRule rule, List<Candidate> eligible,
        Map<Integer, SubjectCategory> appliedSubjects, boolean limitKoreanHistory) {
        Set<Integer> selected = new LinkedHashSet<>();
        Map<SubjectCategory, List<Candidate>> bySubject = eligible.stream().collect(Collectors.groupingBy(
            candidate -> appliedSubjects.getOrDefault(candidate.index(), candidate.course().subjectCategory()),
            () -> new EnumMap<>(SubjectCategory.class), Collectors.toList()));
        Set<Integer> koreanHistoryIndexes = eligible.stream().filter(this::isKoreanHistory)
            .map(Candidate::index).collect(Collectors.toSet());
        for (List<Candidate> subjectCourses : bySubject.values()) {
            addTopCourses(selected, subjectCourses.stream().filter(candidate -> !candidate.course().careerSubject()).toList(),
                rule.getSelectionCount(), limitKoreanHistory, koreanHistoryIndexes);
            addTopCourses(selected, subjectCourses.stream().filter(candidate -> candidate.course().careerSubject()).toList(),
                rule.getAchievementSelectionCount(), limitKoreanHistory, koreanHistoryIndexes);
        }
        return selected;
    }

    private void addTopCourses(Set<Integer> selected, List<Candidate> candidates, int limit,
        boolean limitKoreanHistory, Set<Integer> koreanHistoryIndexes) {
        int added = 0;
        boolean koreanHistorySelected = selected.stream().anyMatch(koreanHistoryIndexes::contains);
        for (Candidate candidate : candidates.stream().sorted(courseComparator()).toList()) {
            if (added >= limit) break;
            if (limitKoreanHistory && isKoreanHistory(candidate) && koreanHistorySelected) continue;
            selected.add(candidate.index());
            added++;
            if (isKoreanHistory(candidate)) koreanHistorySelected = true;
        }
    }

    private boolean isKoreanHistory(Candidate candidate) {
        return isKoreanHistory(candidate.course());
    }

    private boolean isKoreanHistory(VerifyGradeRequest.CourseGrade course) {
        return course.courseName().replaceAll("\\s", "").contains("한국사");
    }

    private <K> Set<Integer> selectTopGroups(EvaluationRule rule, List<Candidate> eligible,
        Function<Candidate, K> classifier, int count, Map<K, Integer> priorities) {
        Map<K, List<Candidate>> groups = eligible.stream().collect(Collectors.groupingBy(classifier));
        Comparator<Map.Entry<K, List<Candidate>>> comparator = Comparator
            .comparing(entry -> groupAverageGrade(entry.getValue()));
        if (priorities != null) comparator = comparator.thenComparing(entry -> priorities.getOrDefault(entry.getKey(), Integer.MAX_VALUE));
        return groups.entrySet().stream().sorted(comparator).limit(count).flatMap(entry -> entry.getValue().stream())
            .map(Candidate::index).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Integer> selectBestSemesterPerGrade(List<Candidate> eligible) {
        Set<Integer> selected = new LinkedHashSet<>();
        Map<Integer, List<Candidate>> byYear = eligible.stream()
            .collect(Collectors.groupingBy(candidate -> candidate.course().schoolYear()));
        for (List<Candidate> yearCourses : byYear.values()) {
            Map<Integer, List<Candidate>> bySemester = yearCourses.stream()
                .collect(Collectors.groupingBy(candidate -> candidate.course().semester()));
            bySemester.values().stream()
                .min(Comparator.comparing(this::groupAverageGrade)
                    .thenComparing(group -> -group.get(0).course().semester()))
                .orElse(List.of()).stream().map(Candidate::index).forEach(selected::add);
        }
        return selected;
    }

    private BigDecimal groupAverageGrade(List<Candidate> candidates) {
        BigDecimal credits = candidates.stream().map(candidate -> candidate.course().credits()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grades = candidates.stream().map(candidate -> candidate.effectiveGrade().multiply(candidate.course().credits()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return grades.divide(credits, 8, RoundingMode.HALF_UP);
    }

    private Comparator<Candidate> courseComparator() {
        return Comparator.comparing(Candidate::effectiveGrade)
            .thenComparing(candidate -> candidate.course().credits(), Comparator.reverseOrder())
            .thenComparing(candidate -> candidate.course().schoolYear(), Comparator.reverseOrder())
            .thenComparing(candidate -> candidate.course().semester(), Comparator.reverseOrder());
    }

    private Set<Integer> indexesOf(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::index).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int gradeFromZScore(BigDecimal raw, BigDecimal mean, BigDecimal standardDeviation) {
        double z = raw.subtract(mean).divide(standardDeviation, 10, RoundingMode.HALF_UP).doubleValue();
        double percentile = (1.0 - normalCdf(z)) * 100.0;
        double[] limits = {4, 11, 23, 40, 60, 77, 89, 96, 100};
        for (int index = 0; index < limits.length; index++) if (percentile <= limits[index]) return index + 1;
        return 9;
    }

    private double normalCdf(double value) {
        double absolute = Math.abs(value);
        double t = 1.0 / (1.0 + 0.2316419 * absolute);
        double density = 0.3989422804014327 * Math.exp(-absolute * absolute / 2.0);
        double probability = 1.0 - density * t * (0.319381530 + t * (-0.356563782
            + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        return value >= 0 ? probability : 1.0 - probability;
    }

    private List<String> buildWarnings(EvaluationRule rule, List<VerifyGradeRequest.CourseGrade> courses,
        List<Candidate> candidates, int included) {
        List<String> warnings = new ArrayList<>();
        if (courses.stream().noneMatch(course -> course.schoolYear() == 3)) warnings.add("3학년 성적이 입력되지 않았습니다.");
        long invalid = candidates.stream().filter(candidate -> !candidate.eligible()).count();
        if (invalid > 0) warnings.add(invalid + "개 과목이 반영 범위 또는 입력값 기준에 따라 제외되었습니다.");
        if (included < courses.size() - invalid) warnings.add("과목 선택 정책 " + rule.getSelectionStrategy() + "이 적용되었습니다.");
        if (rule.getSourceDocument() == null || rule.getSourceDocument().isBlank())
            warnings.add("규칙에 모집요강 출처가 등록되지 않았습니다.");
        return warnings;
    }

    private void validateRule(CreateEvaluationRuleRequest request) {
        BigDecimal gradeSum = request.gradeWeights().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.applyGradeWeights() && gradeSum.compareTo(ONE_HUNDRED) != 0)
            throw CustomException.of(INVALID_EVALUATION_RULE, "학년 반영 비율의 합은 100이어야 합니다.");
        if (!request.applyGradeWeights() && request.normalizeGradeWeights())
            throw CustomException.of(INVALID_EVALUATION_RULE,
                "학년별 가중치를 적용하지 않는 규칙은 학년별 평균 정규화를 사용할 수 없습니다.");
        if (request.subjectWeights().stream().allMatch(value -> value.signum() == 0))
            throw CustomException.of(INVALID_EVALUATION_RULE, "교과 반영 가중치는 하나 이상 0보다 커야 합니다.");
        if (request.minimumCourseCount() > 0 && request.selectionStrategy() == SelectionStrategy.TOP_N_COURSES
            && request.minimumCourseCount() > request.selectionCount())
            throw CustomException.of(INVALID_EVALUATION_RULE, "최소 반영과목 수는 선택 과목 수보다 클 수 없습니다.");
        if (request.selectionStrategy() != SelectionStrategy.ALL_COURSES
            && request.selectionStrategy() != SelectionStrategy.BEST_SEMESTER_PER_GRADE
            && request.selectionCount() < 1)
            throw CustomException.of(INVALID_EVALUATION_RULE, "선택형 규칙의 반영 개수는 1 이상이어야 합니다.");
    }

    private EvaluationRule findRule(Long ruleId) {
        return ruleRepository.findOneById(ruleId)
            .orElseThrow(() -> CustomException.of(EVALUATION_RULE_NOT_FOUND, ruleId.toString()));
    }

    private void requireStatus(EvaluationRule rule, EvaluationRuleStatus expected) {
        if (rule.getStatus() != expected) {
            throw CustomException.of(
                INVALID_EVALUATION_RULE_STATUS,
                "필요 상태: %s, 현재 상태: %s".formatted(expected, rule.getStatus())
            );
        }
    }

    private record Candidate(
        int index,
        VerifyGradeRequest.CourseGrade course,
        BigDecimal effectiveGrade,
        BigDecimal convertedScore,
        String exclusionReason
    ) {
        boolean eligible() {
            return exclusionReason == null;
        }
    }

    private record CourseSelection(
        Set<Integer> indexes,
        Map<Integer, SubjectCategory> appliedSubjectCategories
    ) {
        static CourseSelection empty() {
            return new CourseSelection(Set.of(), Map.of());
        }

        static CourseSelection of(Set<Integer> indexes) {
            return new CourseSelection(indexes, Map.of());
        }

        SubjectCategory appliedSubjectCategory(Candidate candidate) {
            return appliedSubjectCategories.getOrDefault(candidate.index(), candidate.course().subjectCategory());
        }
    }
}
