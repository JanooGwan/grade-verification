package com.jinhakapply.gradevalidation.global.util;

import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalizePolicyText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
