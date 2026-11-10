package com.team.resume.profile.support;

import java.util.List;

import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.vo.ProfileVO;

/** 档案实体 → API VO 的共享映射。 */
public final class ProfileVos {

    private ProfileVos() {
    }

    public static ProfileVO.ProjectVO project(ProfileProject project) {
        return new ProfileVO.ProjectVO(
                project.getId(),
                project.getProjectName(),
                project.getRole(),
                project.getDescription(),
                project.getTechStack() == null ? List.of() : List.copyOf(project.getTechStack()),
                project.getStartDate(),
                project.getEndDate());
    }

    public static ProfileVO.ExperienceVO experience(ProfileExperience experience) {
        return new ProfileVO.ExperienceVO(
                experience.getId(),
                experience.getCompany(),
                experience.getPosition(),
                experience.getDescription(),
                experience.getAchievements(),
                experience.getStartDate(),
                experience.getEndDate());
    }
}
