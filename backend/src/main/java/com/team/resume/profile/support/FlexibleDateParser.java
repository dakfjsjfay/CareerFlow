package com.team.resume.profile.support;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * 解析 AI 抽取出的日期。
 * 支持 2025-03、2025-03-01、2025/3/1、2025年3月 等常见写法；
 * 「至今 / 现在 / present」等表示未结束，返回 null（数据库 end_date 为空即代表至今）。
 * 无法解析时返回 null，不影响证据确认。
 */
public final class FlexibleDateParser {

    private FlexibleDateParser() {
    }

    public static LocalDate parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty() || text.matches("(?i)至今|现在|目前|present|now|current|today")) {
            return null;
        }

        text = text.replace("年", "-")
                .replace("月", "-")
                .replace("日", "")
                .replace('/', '-')
                .replace('.', '-')
                .replaceAll("-+$", "");

        String[] parts = text.split("-");
        try {
            if (parts.length >= 3) {
                return date(parts[0], parts[1], parts[2]);
            }
            if (parts.length == 2) {
                return date(parts[0], parts[1], "1");
            }
            if (parts.length == 1) {
                int year = Integer.parseInt(parts[0].trim());
                return valid(year) ? LocalDate.of(year, 1, 1) : null;
            }
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
        return null;
    }

    private static LocalDate date(String yearText, String monthText, String dayText) {
        int year = Integer.parseInt(yearText.trim());
        int month = Integer.parseInt(monthText.trim());
        int day = Integer.parseInt(dayText.trim());
        if (!valid(year) || month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        return LocalDate.of(year, month, day);
    }

    /** 限制在合理年份范围内，避免把 "3" 之类误解析成公元 3 年。 */
    private static boolean valid(int year) {
        return year >= 1900 && year <= 2100;
    }
}
