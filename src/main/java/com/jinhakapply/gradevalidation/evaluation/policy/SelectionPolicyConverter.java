package com.jinhakapply.gradevalidation.evaluation.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SelectionPolicyConverter implements AttributeConverter<CourseSelectionPolicy, String> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public String convertToDatabaseColumn(CourseSelectionPolicy attribute) {
        if (attribute == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("선언형 과목 선택 정책을 JSON으로 변환할 수 없습니다.", exception);
        }
    }

    @Override
    public CourseSelectionPolicy convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return OBJECT_MAPPER.readValue(dbData, CourseSelectionPolicy.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("저장된 선언형 과목 선택 정책을 읽을 수 없습니다.", exception);
        }
    }
}
