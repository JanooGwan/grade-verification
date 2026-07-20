package com.jinhakapply.gradevalidation.assistant.model;

import java.util.List;
import java.util.Map;

public record QueryResult(List<Map<String, Object>> rows) {
    public int rowCount() {
        return rows.size();
    }
}
