package com.team.resume.matching.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.team.resume.common.BusinessException;
import com.team.resume.job.entity.Job;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.matching.dto.FeedbackRequest;
import com.team.resume.matching.entity.UserFeedback;
import com.team.resume.matching.mapper.UserFeedbackMapper;
import com.team.resume.matching.vo.FeedbackVO;

/**
 * 偏好反馈（docs/api.md §12）：写入 user_feedback，供推荐排序后置规则使用。
 * 与投递状态备注（application_history.note）区分，两者不混用。
 */
@Service
public class FeedbackService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "NOT_INTERESTED", "LOCATION_MISMATCH", "SALARY_MISMATCH", "SKILL_ALREADY_HAVE");

    private final UserFeedbackMapper feedbackMapper;
    private final JobMapper jobMapper;

    public FeedbackService(UserFeedbackMapper feedbackMapper, JobMapper jobMapper) {
        this.feedbackMapper = feedbackMapper;
        this.jobMapper = jobMapper;
    }

    public FeedbackVO submit(Long userId, FeedbackRequest request) {
        String type = request.getFeedbackType().trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException(400,
                    "非法的反馈类型，可选值：" + String.join(" / ", ALLOWED_TYPES));
        }
        Job job = jobMapper.selectById(request.getJobId());
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setJobId(job.getId());
        feedback.setFeedbackType(type);
        feedback.setFeedbackContent(trimToNull(request.getFeedbackContent()));
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        return new FeedbackVO(feedback.getId());
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
