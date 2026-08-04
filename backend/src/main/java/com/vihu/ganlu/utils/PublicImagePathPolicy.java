package com.vihu.ganlu.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一识别公共图片生命周期管理的本地路径。
 */
public final class PublicImagePathPolicy {
    private static final String COURSE_COVER_NAMESPACE = "images/materials/";
    private static final Pattern MANAGED_PATH = Pattern.compile(
            "^(?:images|images_pending)/(?:([1-9][0-9]*)/)?"
                    + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png|webp)$");
    private static final Pattern COURSE_COVER_EXTENSION = Pattern.compile("(?i).+\\.(?:jpe?g|png|webp)$");
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

    /**
     * 课件封面由课件模块独立管理，不属于 public_image_asset 账本。
     */
    public static boolean isCourseCoverNamespace(String value) {
        if (value == null) return false;
        String normalized = value.trim().replace('\\', '/').replaceFirst("^/+", "");
        return normalized.startsWith(COURSE_COVER_NAMESPACE);
    }

    /**
     * 识别数据库中有效课件记录引用的安全本地图片路径。除现行 materials 命名空间外，
     * 还需保护补丁 30 从 thumbnail_url 回填的历史 images/... 封面。
     */
    public static String normalizeCourseCoverReference(String value) {
        if (value == null) return null;
        String normalized = value.trim().replace('\\', '/').replaceFirst("^/+", "");
        if (!normalized.startsWith("images/") || normalized.contains("//")
                || !COURSE_COVER_EXTENSION.matcher(normalized).matches()) {
            return null;
        }
        String[] parts = normalized.split("/");
        if (parts.length < 2) return null;
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return null;
        }
        return normalized;
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
