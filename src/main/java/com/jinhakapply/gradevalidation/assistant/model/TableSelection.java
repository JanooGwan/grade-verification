package com.jinhakapply.gradevalidation.assistant.model;

import java.util.List;

public record TableSelection(List<String> tables, boolean needsFullSchema, String reason) {
}
