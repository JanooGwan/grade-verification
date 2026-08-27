package com.jinhakapply.gradevalidation.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhakapply.gradevalidation.admission.dto.CreateAdmissionTrackRequest;
import com.jinhakapply.gradevalidation.admission.dto.CreateStudentApplicationRequest;
import com.jinhakapply.gradevalidation.admission.dto.UpdateAdmissionTrackRequest;
import com.jinhakapply.gradevalidation.admission.dto.UpdateRecruitmentUnitRequest;
import com.jinhakapply.gradevalidation.assistant.config.AssistantProperties;
import com.jinhakapply.gradevalidation.assistant.dto.AssistantMessageRequest;
import com.jinhakapply.gradevalidation.evaluation.dto.CreateEvaluationRuleRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RequestValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void rejectsInvalidAdmissionTrackFields() {
        var violations = VALIDATOR.validate(new CreateAdmissionTrackRequest(0L, 1999, " "));

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("universityId", "admissionYear", "name");
    }

    @Test
    void rejectsNonPositiveApplicationResourceId() {
        var violations = VALIDATOR.validate(new CreateStudentApplicationRequest(-1L));

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
            .containsExactly("recruitmentUnitId");
    }

    @Test
    void requiresExplicitActiveValueForUpdateRequests() {
        var trackViolations = VALIDATOR.validate(new UpdateAdmissionTrackRequest("학생부교과", null));
        var unitViolations = VALIDATOR.validate(new UpdateRecruitmentUnitRequest("CS01", "컴퓨터공학부", null));

        assertThat(trackViolations).extracting(violation -> violation.getPropertyPath().toString())
            .containsExactly("active");
        assertThat(unitViolations).extracting(violation -> violation.getPropertyPath().toString())
            .containsExactly("active");
    }

    @Test
    void validatesAssistantQuestionAndConversationIdentifier() {
        var blankQuestion = VALIDATOR.validate(new AssistantMessageRequest(" ", "conversation-1"));
        var invalidConversation = VALIDATOR.validate(new AssistantMessageRequest("대학 수는?", "invalid id!"));

        assertThat(blankQuestion).extracting(violation -> violation.getPropertyPath().toString())
            .contains("question");
        assertThat(invalidConversation).extracting(violation -> violation.getPropertyPath().toString())
            .containsExactly("conversationId");
    }

    @Test
    void validatesAssistantDatabaseSafetyLimits() {
        AssistantProperties properties = new AssistantProperties(
            true,
            new AssistantProperties.Anthropic("key", "model", "url", "version"),
            new AssistantProperties.Database("jdbc", "reader", "password"),
            new AssistantProperties.Query(101, 0)
        );

        assertThat(VALIDATOR.validate(properties))
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("query.maxRows", "query.timeoutSeconds");
    }

    @Test
    void requiresExplicitGradeWeightApplicationValue() throws Exception {
        CreateEvaluationRuleRequest request = new ObjectMapper().readValue(
            """
                {
                  "admissionYear": 2027,
                  "version": 1,
                  "selectionCount": 0,
                  "achievementSelectionCount": 0,
                  "minimumCourseCount": 0,
                  "includeThirdYearSecondSemester": false,
                  "includeThirdYearSecondSemesterForGraduates": false,
                  "includeProfessionalCourses": false,
                  "normalizeGradeWeights": false,
                  "intermediateScale": 0,
                  "finalScale": 0
                }
                """, CreateEvaluationRuleRequest.class
        );

        assertThat(VALIDATOR.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("applyGradeWeights");
    }
}
