package com.jinhakapply.gradevalidation.assistant.model;

import java.util.List;

public record SqlPlan(String sql, List<String> sourceTables, String reason) {
}
