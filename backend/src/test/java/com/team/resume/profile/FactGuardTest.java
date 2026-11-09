package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.team.resume.profile.support.FactGuard;

class FactGuardTest {

    @Test
    void matchesVerbatimValuesIgnoringCaseAndPunctuation() {
        assertThat(FactGuard.isGrounded("我用 Java 做过项目", "Java")).isTrue();
        assertThat(FactGuard.isGrounded("我用 java 做过项目", "Java")).isTrue();
        assertThat(FactGuard.isGrounded("我叫张三。", "张三")).isTrue();
        assertThat(FactGuard.isGrounded("某某大学计算机专业", "某某大学")).isTrue();
        assertThat(FactGuard.isGrounded("我学过 C++ 和 C#", "C++")).isTrue();
        assertThat(FactGuard.isGrounded("我学过 C++ 和 C#", "C#")).isTrue();
    }

    @Test
    void matchesNumbersAndLightRewrites() {
        assertThat(FactGuard.isGrounded("2027 年毕业", "2027")).isTrue();
        assertThat(FactGuard.isGrounded("电话 138 0000 0000", "13800000000")).isTrue();
        assertThat(FactGuard.isGrounded("我想找 Java 后端岗位", "Java 后端开发")).isTrue();
    }

    @Test
    void rejectsValuesNotSupportedBySource() {
        assertThat(FactGuard.isGrounded("????", "学生管理系统")).isFalse();
        assertThat(FactGuard.isGrounded("我学过 Java", "MySQL")).isFalse();
        assertThat(FactGuard.isGrounded("", "Java")).isFalse();
        assertThat(FactGuard.isGrounded("我叫张三", null)).isFalse();
    }
}
