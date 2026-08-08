package com.vihu.ganlu.utils;

import java.util.regex.Pattern;

/** Normalizes local course-material paths before reference comparison or deletion. */
public final class MaterialPathPolicy {
    private static final Pattern EXTERNAL_URL = Pattern.compile("(?i)^https?://.+");

    private MaterialPathPolicy() {
    }

    public static String normalizeLocalPath(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || EXTERNAL_URL.matcher(trimmed).matches()) return null;
        String normalized = trimmed.replace('\\', '/').replaceFirst("^/+", "");
        if (normalized.isEmpty() || normalized.contains("//") || normalized.indexOf('\0') >= 0) return null;
        String[] parts = normalized.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part) || part.contains(":")) return null;
        }
        return normalized;
    }
}
