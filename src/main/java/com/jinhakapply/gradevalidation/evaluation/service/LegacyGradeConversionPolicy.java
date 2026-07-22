package com.jinhakapply.gradevalidation.evaluation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.jinhakapply.gradevalidation.global.exception.CustomException;

import static com.jinhakapply.gradevalidation.global.code.ApiResponseCode.INVALID_APPLICATION_SCORE_INPUT;

public final class LegacyGradeConversionPolicy {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal[] PERCENTILE_LIMITS = {
        new BigDecimal("4"), new BigDecimal("11"), new BigDecimal("23"),
        new BigDecimal("40"), new BigDecimal("60"), new BigDecimal("77"),
        new BigDecimal("89"), new BigDecimal("96"), new BigDecimal("100")
    };

    private LegacyGradeConversionPolicy() {}

    public static BigDecimal rankPercentile(int rank, Integer tiedRankCount, int cohortSize, int scale) {
        if (rank < 1 || cohortSize < 1 || rank > cohortSize) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, "석차는 1 이상 재적수 이하여야 합니다.");
        }
        int tied = tiedRankCount == null ? 1 : tiedRankCount;
        if (tied < 1 || rank + tied - 1 > cohortSize) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, "동석차 인원 범위가 재적수를 초과합니다.");
        }
        BigDecimal adjustedRank = BigDecimal.valueOf(rank)
            .add(BigDecimal.valueOf(tied - 1L).divide(TWO, 10, RoundingMode.HALF_UP));
        return adjustedRank.multiply(ONE_HUNDRED)
            .divide(BigDecimal.valueOf(cohortSize), scale, RoundingMode.HALF_UP);
    }

    public static int gradeForPercentile(BigDecimal percentile) {
        if (percentile == null || percentile.signum() < 0 || percentile.compareTo(ONE_HUNDRED) > 0) {
            throw CustomException.of(INVALID_APPLICATION_SCORE_INPUT, "석차백분율은 0 이상 100 이하여야 합니다.");
        }
        for (int index = 0; index < PERCENTILE_LIMITS.length; index++) {
            if (percentile.compareTo(PERCENTILE_LIMITS[index]) <= 0) return index + 1;
        }
        return 9;
    }
}
