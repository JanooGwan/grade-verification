package com.jinhakapply.gradevalidation.operation.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import com.jinhakapply.gradevalidation.operation.dto.RuleChangeEntry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class RuleChangeHistoryService {
    private final List<RuleChangeEntry> entries;
    public RuleChangeHistoryService(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("rule-change-history.json").getInputStream()) {
            entries = List.copyOf(Arrays.asList(mapper.readValue(input, RuleChangeEntry[].class)));
        }
    }
    public List<RuleChangeEntry> findAll() { return entries; }
}
