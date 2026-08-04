package com.jinhakapply.gradevalidation.evaluation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.CourseFilter;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.GroupDimension;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionMetric;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionStage;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SelectionStageType;
import com.jinhakapply.gradevalidation.evaluation.policy.CourseSelectionPolicy.SortDirection;
import com.jinhakapply.gradevalidation.evaluation.policy.DeclarativeSelectionPolicyEngine.CourseCandidate;
import org.junit.jupiter.api.Test;

class DeclarativeSelectionPolicyEngineTest {
    private final DeclarativeSelectionPolicyEngine engine = new DeclarativeSelectionPolicyEngine();

    @Test
    void selectsTopCoursesFromConfiguredSubjects() {
        CourseSelectionPolicy policy = policy(
            new CourseFilter(Set.of(SubjectCategory.KOREAN, SubjectCategory.ENGLISH), true, true, false),
            topCourses(GroupDimension.NONE, SelectionMetric.CONVERTED_SCORE, SortDirection.DESC, 2)
        );

        Set<Integer> selected = engine.select(policy, List.of(
            course(0, 1, 1, SubjectCategory.KOREAN, "95", "95"),
            course(1, 1, 1, SubjectCategory.MATH, "100", "100"),
            course(2, 2, 1, SubjectCategory.ENGLISH, "98", "98"),
            course(3, 3, 1, SubjectCategory.KOREAN, "90", "90")
        ));

        assertThat(selected).containsExactly(2, 0);
    }

    @Test
    void selectsTopCoursesPerSubjectWithoutAddingAUniversitySpecificEnum() {
        CourseSelectionPolicy policy = policy(
            CourseFilter.all(),
            topCourses(GroupDimension.SUBJECT, SelectionMetric.CONVERTED_SCORE, SortDirection.DESC, 1)
        );

        Set<Integer> selected = engine.select(policy, List.of(
            course(0, 1, 1, SubjectCategory.KOREAN, "90", "90"),
            course(1, 2, 1, SubjectCategory.KOREAN, "95", "95"),
            course(2, 1, 1, SubjectCategory.MATH, "93", "93"),
            course(3, 2, 1, SubjectCategory.MATH, "91", "91")
        ));

        assertThat(selected).containsExactly(1, 2);
    }

    @Test
    void selectsBestSemesterWithinEachSchoolYear() {
        CourseSelectionPolicy policy = policy(
            CourseFilter.all(),
            topGroups(GroupDimension.SCHOOL_YEAR, GroupDimension.SEMESTER,
                SelectionMetric.EFFECTIVE_GRADE, SortDirection.ASC, 1)
        );

        Set<Integer> selected = engine.select(policy, List.of(
            course(0, 1, 1, SubjectCategory.KOREAN, "3", "80"),
            course(1, 1, 2, SubjectCategory.KOREAN, "1", "100"),
            course(2, 2, 1, SubjectCategory.MATH, "2", "90"),
            course(3, 2, 2, SubjectCategory.MATH, "4", "70")
        ));

        assertThat(selected).containsExactly(1, 2);
    }

    @Test
    void selectsBestSemestersAcrossAllYears() {
        CourseSelectionPolicy policy = policy(
            CourseFilter.all(),
            topGroups(GroupDimension.NONE, GroupDimension.SCHOOL_YEAR_AND_SEMESTER,
                SelectionMetric.CONVERTED_SCORE, SortDirection.DESC, 2)
        );

        Set<Integer> selected = engine.select(policy, List.of(
            course(0, 1, 1, SubjectCategory.KOREAN, "3", "80"),
            course(1, 1, 2, SubjectCategory.KOREAN, "1", "100"),
            course(2, 2, 1, SubjectCategory.MATH, "2", "90"),
            course(3, 2, 2, SubjectCategory.MATH, "4", "70")
        ));

        assertThat(selected).containsExactly(1, 2);
    }

    private CourseSelectionPolicy policy(CourseFilter filter, SelectionStage... stages) {
        return new CourseSelectionPolicy(1, filter, List.of(stages));
    }

    private SelectionStage topCourses(GroupDimension partitionBy, SelectionMetric metric,
        SortDirection direction, int limit) {
        return new SelectionStage(SelectionStageType.TOP_COURSES, partitionBy, GroupDimension.NONE,
            metric, direction, limit);
    }

    private SelectionStage topGroups(GroupDimension partitionBy, GroupDimension groupBy,
        SelectionMetric metric, SortDirection direction, int limit) {
        return new SelectionStage(SelectionStageType.TOP_GROUPS, partitionBy, groupBy, metric, direction, limit);
    }

    private CourseCandidate course(int index, int year, int semester, SubjectCategory subject,
        String grade, String score) {
        return new CourseCandidate(index, year, semester, subject, false, false, BigDecimal.ONE,
            new BigDecimal(grade), new BigDecimal(score));
    }
}
