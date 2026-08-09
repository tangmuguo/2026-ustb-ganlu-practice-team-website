package com.vihu.ganlu.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 课件通识课程可用于新增上传的固定科目。
 *
 * <p>历史课件仍可关联到已停用的旧科目，因此不能通过删除旧记录实现该限制。</p>
 */
public final class GeneralCourseSubjectPolicy {
    private static final Set<String> SUPPORTED_SUBJECTS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("语文", "数学", "英语")));

    private GeneralCourseSubjectPolicy() {
    }

    public static boolean isSupported(String courseName) {
        return SUPPORTED_SUBJECTS.contains(courseName);
    }
}
