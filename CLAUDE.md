# AI Resume System - 项目开发规范

## 1. 项目介绍

本项目是智能应用开发大赛参赛作品：自然语言对话式简历生成与精准投放系统（CareerFlow-AI 智能求职助手）。

团队：3 人
开发周期：14 天

## 2. 技术栈

前端：
Vue 3 + Vite + TypeScript + Element Plus + Pinia + Axios + ECharts

后端：
Java 17 + Spring Boot 3 + MyBatis-Plus + Spring Security + JWT + BCrypt

数据库：
MySQL 8

AI：
DeepSeek V4 API（仅后端调用）

部署：
Docker Compose（Day 13-14）

## 3. 工程结构

- frontend：前端代码
- backend：后端代码
- database：SQL 文件（init.sql 建表、seed.sql 模拟岗位数据）
- docs：项目设计文档（唯一依据：requirements / architecture / database / api / acceptance / development-plan）

## 4. 统一开发规范

1. 开发前必须阅读相关 docs 文档。
2. 不得擅自修改 API 契约（docs/api.md 为准）。
3. 不得擅自修改数据库字段（docs/database.md 与 database/ 下 SQL 为准）。
4. 前端不得直接调用 DeepSeek API。
5. AI Key 必须存储在后端环境变量中。
6. 所有业务接口必须验证用户权限（user_id 数据隔离）。
7. 不允许编造用户简历经历；未确认信息必须标记。
8. 不允许覆盖历史简历版本。
9. 修改代码后必须执行对应测试。
10. 不要修改不属于当前任务的模块。

## 5. 前后端接口规范

- 所有接口统一使用 /api 前缀。
- 返回格式：{ "code": 200, "message": "success", "data": {} }
- 接口定义以 docs/api.md 为唯一依据。
- 参数 camelCase，数据库字段 snake_case。

## 6. 数据库规范

- 所有数据库设计以 docs/database.md 和 database/ 中的 SQL 文件为准。
- 修改数据库必须同步更新文档和 SQL。

## 7. AI 开发规范

- AI 输出必须经过后端结构校验与事实校验。
- 用户没有明确提供的信息，不允许作为已确认的个人事实。
- 大模型负责理解与生成；Java 负责规则、计算、状态、数据。

## 8. 开发要求

每次任务：
1. 先阅读现有代码。
2. 说明实施计划。
3. 控制修改范围。
4. 执行测试。
5. 汇报修改文件和测试结果。

不要一次性重构整个项目。
