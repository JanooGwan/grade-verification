package com.jinhakapply.gradevalidation.evaluation.domain;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "evaluation_rule_extraction_evidence")
@NoArgsConstructor(access = PROTECTED)
public class EvaluationRuleExtractionEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "extraction_id", nullable = false)
    private EvaluationRuleExtraction extraction;

    @Column(name = "field_key", nullable = false, length = 50)
    private String fieldKey;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(nullable = false, length = 1500)
    private String excerpt;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    public static EvaluationRuleExtractionEvidence create(
        EvaluationRuleExtraction extraction,
        String fieldKey,
        int pageNumber,
        String excerpt,
        BigDecimal confidence
    ) {
        EvaluationRuleExtractionEvidence evidence = new EvaluationRuleExtractionEvidence();
        evidence.extraction = extraction;
        evidence.fieldKey = fieldKey;
        evidence.pageNumber = pageNumber;
        evidence.excerpt = excerpt.length() > 1500 ? excerpt.substring(0, 1500) : excerpt;
        evidence.confidence = confidence;
        return evidence;
    }
}
