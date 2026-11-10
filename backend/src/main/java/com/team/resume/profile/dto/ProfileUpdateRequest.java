package com.team.resume.profile.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 手动更新档案（docs/api.md §6）。
 * 字段为 null 表示不修改；字符串传空串/空白表示清空；skills 传数组表示整体替换。
 */
@Data
public class ProfileUpdateRequest {

    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String realName;

    @Size(max = 11, message = "电话不能超过 11 个字符")
    private String phone;

    @Size(max = 100, message = "学校不能超过 100 个字符")
    private String school;

    @Size(max = 100, message = "专业不能超过 100 个字符")
    private String major;

    @Size(max = 50, message = "学历不能超过 50 个字符")
    private String degree;

    @Min(value = 1900, message = "毕业年份不合法")
    @Max(value = 2100, message = "毕业年份不合法")
    private Integer graduationYear;

    @Size(max = 50, message = "技能不能超过 50 条")
    private List<@Size(max = 100, message = "单个技能不能超过 100 个字符") String> skills;

    @Size(max = 10, message = "目标岗位不能超过 10 个字符")
    private String targetPosition;

    @Size(max = 4, message = "意向城市不能超过 4 个字符")
    private String targetCity;

    @Size(max = 50, message = "期望薪资不能超过 50 个字符")
    private String expectedSalary;
}
