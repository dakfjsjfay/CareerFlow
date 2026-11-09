package com.team.resume.profile.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * AI 抽取结果的事实校验（docs/architecture.md §4）：
 * 每一条准备落库的信息都必须能在用户本轮原话中找到依据，否则丢弃。
 * <p>
 * 判定顺序：
 * 1. 归一化（去空白/标点、转小写）后整串包含 → 通过；
 * 2. 数字型内容（年份/电话等）按数字序列包含 → 通过；
 * 3. 较长文本按 2-gram 覆盖率 ≥ 60% → 通过（容忍模型的轻微改写）。
 * <p>
 * 宁可少收一条，也不把 AI 编造的内容写进档案。
 */
public final class FactGuard {

    /** 保留字母、数字以及 C++/C# 这类技能名中的符号，其余空白/标点忽略。 */
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}+#]");
    private static final Pattern NON_DIGIT = Pattern.compile("\\D");

    private FactGuard() {
    }

    public static boolean isGrounded(String sourceText, String value) {
        if (sourceText == null || sourceText.isBlank() || value == null || value.isBlank()) {
            return false;
        }
        String source = normalize(sourceText);
        String target = normalize(value);
        if (target.isEmpty()) {
            return false;
        }
        if (source.contains(target)) {
            return true;
        }

        // 年份、电话等数字：用户可能写成 "2027 年" / "138 0000 0000"
        String sourceDigits = NON_DIGIT.matcher(sourceText).replaceAll("");
        String valueDigits = NON_DIGIT.matcher(value).replaceAll("");
        if (!valueDigits.isEmpty() && sourceDigits.contains(valueDigits)) {
            return true;
        }

        // 较长文本：2-gram 覆盖率 ≥ 60%，允许「计算机专业」↔「计算机相关专业」这类轻微改写
        if (target.length() >= 4) {
            int total = target.length() - 1;
            int hits = 0;
            for (int i = 0; i < total; i++) {
                if (source.contains(target.substring(i, i + 2))) {
                    hits++;
                }
            }
            return total > 0 && hits * 10 >= total * 6;
        }
        return false;
    }

    private static String normalize(String text) {
        return NON_WORD.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
