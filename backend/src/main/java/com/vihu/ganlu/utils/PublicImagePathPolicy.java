package com.vihu.ganlu.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一识别公共图片生命周期管理的本地路径。
 */
public final class PublicImagePathPolicy {
    private static final Pattern MANAGED_PATH = Pattern.compile(
            "^(?:images|images_pending)/(?:([1-9][0-9]*)/)?"
                    + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png|webp)$");
    private static final Pattern EXTERNAL_URL = Pattern.compile("(?i)^https?://.+");

    private PublicImagePathPolicy() {
    }

    public static boolean isExternalUrl(String value) {
        return value != null && EXTERNAL_URL.matcher(value.trim()).matches();
    }

    public static String normalizeManagedPath(String value) {
        if (value == null) return null;
        String normalized = value.trim().replace('\\', '/').replaceFirst("^/+", "");
        return MANAGED_PATH.matcher(normalized).matches() ? normalized : null;
    }

    public static Integer ownerFromPath(String value) {
        String normalized = normalizeManagedPath(value);
        if (normalized == null) return null;
        Matcher matcher = MANAGED_PATH.matcher(normalized);
        if (!matcher.matches() || matcher.group(1) == null) return null;
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
