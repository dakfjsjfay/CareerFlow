-- ============================================================
-- CareerFlow-AI 智能求职助手 数据库初始化脚本
-- D1：仅建库；17 张业务表在 D2 按 docs/database.md 编写追加
-- 执行方式：mysql -u root -p < init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS ai_resume
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- D2 将在此追加：sys_user / user_profile / profile_project /
-- profile_experience / profile_evidence / chat_session / chat_message /
-- resume / resume_version / job / job_requirement / match_result /
-- match_detail / optimization_task / job_application /
-- application_history / user_feedback（共 17 张，以 docs/database.md 为准）
