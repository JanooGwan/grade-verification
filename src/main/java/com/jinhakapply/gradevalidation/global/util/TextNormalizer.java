package com.jinhakapply.gradevalidation.global.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalizePolicyText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static String normalizeCourseName(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replaceAll("[\\s·ㆍ・･]+", "")
            .toLowerCase(Locale.ROOT);
    }
}
