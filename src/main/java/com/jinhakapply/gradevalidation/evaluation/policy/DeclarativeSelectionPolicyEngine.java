package com.jinhakapply.gradevalidation.evaluation.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.CourseFilter;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.GroupDimension;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionMetric;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionStage;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SortDirection;
import org.springframework.stereotype.Component;

@Component
public class DeclarativeSelectionPolicyEngine {

    public Set<Integer> select(CourseSelectionPolicy policy, List<CourseCandidate> input) {
        List<String> errors = SelectionPolicyValidator.validate(policy);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));

        List<CourseCandidate> selected = input.stream()
            .filter(candidate -> matches(policy.filter(), candidate))
            .toList();
        for (SelectionStage stage : policy.stages()) {
            selected = apply(stage, selected);
        }
        return selected.stream().map(CourseCandidate::index)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean matches(CourseFilter filter, CourseCandidate candidate) {
        if (!filter.subjects().isEmpty() && !filter.subjects().contains(candidate.subject())) return false;
        if (candidate.professionalCourse()) return filter.includeProfessionalCourses();
        if (candidate.careerCourse()) return filter.includeCareerCourses();
        return filter.includeGeneralCourses();
    }

    private List<CourseCandidate> apply(SelectionStage stage, List<CourseCandidate> candidates) {
        Map<Object, List<CourseCandidate>> partitions = group(candidates, stage.partitionBy());
        List<CourseCandidate> result = new ArrayList<>();
        for (List<CourseCandidate> partition : partitions.values()) {
            if (stage.type() == CourseSelectionPolicy.SelectionStageType.TOP_COURSES) {
                partition.stream().sorted(courseComparator(stage.metric(), stage.direction()))
                    .limit(stage.limit()).forEach(result::add);
            } else {
                selectGroups(stage, partition).forEach(result::add);
            }
        }
        return List.copyOf(result);
    }

    private List<CourseCandidate> selectGroups(SelectionStage stage, List<CourseCandidate> candidates) {
        return group(candidates, stage.groupBy()).values().stream()
            .sorted(groupComparator(stage.metric(), stage.direction()))
            .limit(stage.limit())
            .flatMap(List::stream)
            .toList();
    }

    private Map<Object, List<CourseCandidate>> group(List<CourseCandidate> candidates, GroupDimension dimension) {
        Map<Object, List<CourseCandidate>> groups = new LinkedHashMap<>();
        for (CourseCandidate candidate : candidates) {
            groups.computeIfAbsent(groupKey(candidate, dimension), ignored -> new ArrayList<>()).add(candidate);
        }
        return groups;
    }

    private Object groupKey(CourseCandidate candidate, GroupDimension dimension) {
        return switch (dimension) {
            case NONE -> "ALL";
            case SCHOOL_YEAR -> candidate.schoolYear();
            case SEMESTER -> candidate.semester();
            case SCHOOL_YEAR_AND_SEMESTER -> candidate.schoolYear() + "-" + candidate.semester();
            case SUBJECT -> candidate.subject();
            case COURSE_TYPE -> candidate.professionalCourse() ? "PROFESSIONAL"
                : candidate.careerCourse() ? "CAREER" : "GENERAL";
        };
    }

    private Comparator<CourseCandidate> courseComparator(SelectionMetric metric, SortDirection direction) {
        Comparator<CourseCandidate> comparator = Comparator.comparing(candidate -> value(candidate, metric));
        if (direction == SortDirection.DESC) comparator = comparator.reversed();
        return comparator.thenComparing(CourseCandidate::credits, Comparator.reverseOrder())
            .thenComparing(CourseCandidate::schoolYear, Comparator.reverseOrder())
            .thenComparing(CourseCandidate::semester, Comparator.reverseOrder())
            .thenComparingInt(CourseCandidate::index);
    }

    private Comparator<List<CourseCandidate>> groupComparator(SelectionMetric metric, SortDirection direction) {
        Comparator<List<CourseCandidate>> comparator = Comparator.comparing(group -> weightedAverage(group, metric));
        if (direction == SortDirection.DESC) comparator = comparator.reversed();
        return comparator.thenComparing(group -> group.stream().mapToInt(CourseCandidate::index).min().orElse(Integer.MAX_VALUE));
    }

    private BigDecimal weightedAverage(List<CourseCandidate> candidates, SelectionMetric metric) {
        BigDecimal credits = candidates.stream().map(CourseCandidate::credits)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (credits.signum() == 0) return BigDecimal.ZERO;
        BigDecimal total = candidates.stream()
            .map(candidate -> value(candidate, metric).multiply(candidate.credits()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(credits, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal value(CourseCandidate candidate, SelectionMetric metric) {
        return switch (metric) {
            case EFFECTIVE_GRADE -> candidate.effectiveGrade();
            case CONVERTED_SCORE -> candidate.convertedScore();
            case CREDITS -> candidate.credits();
        };
    }

    public record CourseCandidate(
        int index,
        int schoolYear,
        int semester,
        SubjectCategory subject,
        boolean careerCourse,
        boolean professionalCourse,
        BigDecimal credits,
        BigDecimal effectiveGrade,
        BigDecimal convertedScore
    ) {}
}
