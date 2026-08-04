package com.jinhakapply.gradevalidation.evaluation.policy;

import java.util.List;
import java.util.Set;

import com.jinhakapply.gradevalidation.evaluation.domain.SubjectCategory;

public record CourseSelectionPolicy(
    int schemaVersion,
    CourseFilter filter,
    List<SelectionStage> stages
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public CourseSelectionPolicy {
        filter = filter == null ? CourseFilter.all() : filter;
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public record CourseFilter(
        Set<SubjectCategory> subjects,
        boolean includeGeneralCourses,
        boolean includeCareerCourses,
        boolean includeProfessionalCourses
    ) {
        public CourseFilter {
            subjects = subjects == null ? Set.of() : Set.copyOf(subjects);
        }

        public static CourseFilter all() {
            return new CourseFilter(Set.of(), true, true, true);
        }
    }

    public record SelectionStage(
        SelectionStageType type,
        GroupDimension partitionBy,
        GroupDimension groupBy,
        SelectionMetric metric,
        SortDirection direction,
        int limit
    ) {
        public SelectionStage {
            partitionBy = partitionBy == null ? GroupDimension.NONE : partitionBy;
            direction = direction == null ? SortDirection.DESC : direction;
        }
    }

    public enum SelectionStageType {
        TOP_COURSES,
        TOP_GROUPS
    }

    public enum GroupDimension {
        NONE,
        SCHOOL_YEAR,
        SEMESTER,
        SCHOOL_YEAR_AND_SEMESTER,
        SUBJECT,
        COURSE_TYPE
    }

    public enum SelectionMetric {
        EFFECTIVE_GRADE,
        CONVERTED_SCORE,
        CREDITS
    }

    public enum SortDirection {
        ASC,
        DESC
    }
}
