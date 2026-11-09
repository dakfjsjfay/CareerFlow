package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.team.resume.profile.support.FlexibleDateParser;

class FlexibleDateParserTest {

    @Test
    void parsesCommonChineseAndNumericFormats() {
        assertThat(FlexibleDateParser.parse("2025-03")).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(FlexibleDateParser.parse("2025-03-15")).isEqualTo(LocalDate.of(2025, 3, 15));
        assertThat(FlexibleDateParser.parse("2025/3/5")).isEqualTo(LocalDate.of(2025, 3, 5));
        assertThat(FlexibleDateParser.parse("2025年6月")).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(FlexibleDateParser.parse("2025年6月18日")).isEqualTo(LocalDate.of(2025, 6, 18));
        assertThat(FlexibleDateParser.parse("2025")).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void returnsNullForOngoingOrInvalidValues() {
        assertThat(FlexibleDateParser.parse("至今")).isNull();
        assertThat(FlexibleDateParser.parse("present")).isNull();
        assertThat(FlexibleDateParser.parse("")).isNull();
        assertThat(FlexibleDateParser.parse(null)).isNull();
        assertThat(FlexibleDateParser.parse("abc")).isNull();
        assertThat(FlexibleDateParser.parse("2025-13")).isNull();
        assertThat(FlexibleDateParser.parse("1800-01")).isNull();
    }
}
