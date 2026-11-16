-- ============================================================
-- CareerFlow-AI 种子数据：14 条模拟岗位（草稿 v1，待团队审核）
-- 依据：docs/development-plan.md（种子 JD 由 Claude 起草、团队审核）
-- 说明：可重复执行。岗位用固定 id + INSERT IGNORE（已存在则保留人工修改）；
--       技能要求按 job_id 先删后插（保证与本文一致）。
-- 执行方式：mysql -u root -p ai_resume < seed.sql
-- ============================================================

-- 强制连接字符集为 utf8mb4。
-- ⚠️ 中文 Windows 的 mysql 客户端默认连接字符集是 GBK，直接重定向本文件
-- （UTF-8 编码）会导致中文被存成乱码甚至报 "Data too long"。
SET NAMES utf8mb4;

USE ai_resume;

-- ---------- Java 后端（6 条） ----------

INSERT IGNORE INTO job (id, company, position, city, salary, jd_content, source_type, external_url, created_at, updated_at) VALUES
(1, '腾讯云智研发', 'Java 开发实习生', '深圳', '8k-12k', '岗位职责：\n1. 参与云服务后端系统设计与开发；\n2. 负责业务接口实现与联调，保障服务稳定性；\n3. 参与代码评审与单元测试编写。\n\n岗位要求：\n1. 熟悉 Java 编程，掌握集合、多线程与面向对象设计；\n2. 熟悉 Spring Boot 框架，有实际项目经验者优先；\n3. 熟悉 MySQL，掌握基本 SQL 优化；\n4. 了解 Redis 缓存原理与常见使用场景；\n5. 了解 Docker 与 Linux 常用命令者加分；\n6. 本科及以上学历，计算机相关专业。', 'DEMO', NULL, NOW(), NOW()),
(2, '字节跳动', '后端开发实习生', '北京', '10k-15k', '岗位职责：\n1. 参与推荐系统与内容平台后端研发；\n2. 负责高并发服务的设计与性能优化。\n\n岗位要求：\n1. 精通 Java，熟悉数据结构与算法；\n2. 熟悉 MySQL 与 Redis，了解分布式缓存；\n3. 了解消息队列（Kafka/RocketMQ）原理；\n4. 有分布式系统开发经验者优先。', 'DEMO', NULL, NOW(), NOW()),
(3, '阿里云', 'Java 研发实习生', '杭州', '面议', '岗位职责：\n1. 参与云计算核心产品研发；\n2. 负责模块设计、编码与测试。\n\n岗位要求：\n1. 熟悉 Java 与 Spring 生态；\n2. 熟悉 MySQL 数据库原理；\n3. 了解分布式系统与高并发架构；\n4. 良好的算法基础与编码习惯。', 'DEMO', NULL, NOW(), NOW()),
(4, '美团', '后端开发实习生', '北京', '8k-13k', '岗位职责：\n1. 参与外卖配送调度系统研发；\n2. 负责业务需求分析、方案设计与实现。\n\n岗位要求：\n1. 熟悉 Java，掌握 Spring Boot；\n2. 熟悉 MySQL，了解索引与事务；\n3. 了解 Redis 缓存与消息队列；\n4. 熟悉 Linux 基本操作。', 'DEMO', NULL, NOW(), NOW()),
(5, '华为', '软件开发实习生', '深圳', '9k-14k', '岗位职责：\n1. 参与通信软件平台开发；\n2. 负责模块编码、调试与文档编写。\n\n岗位要求：\n1. 熟悉 Java 或 C++；\n2. 扎实的数据结构与操作系统基础；\n3. 了解 MySQL 与计算机网络；\n4. 学习能力强，有团队协作精神。', 'DEMO', NULL, NOW(), NOW()),
(6, '平安科技', 'Java 开发实习生', '深圳', '6k-10k', '岗位职责：\n1. 参与金融科技业务系统开发；\n2. 负责接口开发与系统维护。\n\n岗位要求：\n1. 熟悉 Java 与 Spring Boot；\n2. 熟悉 MySQL 数据库；\n3. 了解 Vue 前端框架者加分；\n4. 了解 Docker 部署者加分。', 'DEMO', NULL, NOW(), NOW());

-- ---------- 前端（3 条） ----------

INSERT IGNORE INTO job (id, company, position, city, salary, jd_content, source_type, external_url, created_at, updated_at) VALUES
(7, '网易', '前端开发实习生', '广州', '7k-11k', '岗位职责：\n1. 参与游戏与社区产品前端开发；\n2. 负责页面性能优化与组件封装。\n\n岗位要求：\n1. 熟悉 Vue 框架；\n2. 掌握 JavaScript 与 HTML/CSS；\n3. 了解小程序开发者加分。', 'DEMO', NULL, NOW(), NOW()),
(8, '携程', '前端开发实习生', '上海', '8k-12k', '岗位职责：\n1. 参与旅行产品 Web 端开发；\n2. 负责移动端页面适配与优化。\n\n岗位要求：\n1. 熟悉 Vue 或 React；\n2. 掌握 TypeScript；\n3. 了解 Vite/Webpack 构建工具。', 'DEMO', NULL, NOW(), NOW()),
(9, '京东', 'Web 前端实习生', '北京', '8k-13k', '岗位职责：\n1. 参与电商平台前端研发；\n2. 负责大促页面性能优化。\n\n岗位要求：\n1. 扎实的 JavaScript 基础；\n2. 熟悉 Vue 框架；\n3. 掌握 CSS 布局与响应式设计；\n4. 了解前端性能优化手段。', 'DEMO', NULL, NOW(), NOW());

-- ---------- 测试（2 条） ----------

INSERT IGNORE INTO job (id, company, position, city, salary, jd_content, source_type, external_url, created_at, updated_at) VALUES
(10, '荣耀', '软件测试实习生', '深圳', '6k-9k', '岗位职责：\n1. 参与手机系统软件测试；\n2. 负责用例设计与缺陷跟踪。\n\n岗位要求：\n1. 掌握软件测试基本理论；\n2. 熟悉 Python 脚本；\n3. 了解 Linux 基本操作；\n4. 了解自动化测试框架者加分。', 'DEMO', NULL, NOW(), NOW()),
(11, '大疆', '测试开发实习生', '深圳', '7k-10k', '岗位职责：\n1. 参与无人机应用测试开发；\n2. 负责自动化测试工具建设。\n\n岗位要求：\n1. 熟悉 Python；\n2. 了解测试框架（pytest 等）；\n3. 了解 MySQL；\n4. 了解 CI/CD 者加分。', 'DEMO', NULL, NOW(), NOW());

-- ---------- 产品 / 数据 / 运维（3 条） ----------

INSERT IGNORE INTO job (id, company, position, city, salary, jd_content, source_type, external_url, created_at, updated_at) VALUES
(12, '哔哩哔哩', '产品经理实习生', '上海', '6k-9k', '岗位职责：\n1. 参与视频社区产品需求分析；\n2. 负责原型设计与需求文档编写。\n\n岗位要求：\n1. 具备需求分析与用户调研能力；\n2. 了解数据分析方法；\n3. 熟练使用 Axure 等原型工具；\n4. 沟通表达能力强。', 'DEMO', NULL, NOW(), NOW()),
(13, '顺丰科技', '数据分析实习生', '深圳', '7k-10k', '岗位职责：\n1. 参与物流业务数据分析；\n2. 负责数据报表与可视化看板建设。\n\n岗位要求：\n1. 熟练使用 SQL；\n2. 熟悉 Python 数据处理；\n3. 熟练使用 Excel；\n4. 了解数据可视化工具。', 'DEMO', NULL, NOW(), NOW()),
(14, '招商银行', '运维开发实习生', '深圳', '8k-12k', '岗位职责：\n1. 参与银行系统运维自动化建设；\n2. 负责监控告警与故障处理。\n\n岗位要求：\n1. 熟悉 Linux 系统；\n2. 掌握 Shell 脚本；\n3. 熟悉 Python；\n4. 了解 Docker 容器技术；\n5. 了解计算机网络。', 'DEMO', NULL, NOW(), NOW());

-- ---------- 技能要求（按 job_id 先删后插，保证可重复执行） ----------

DELETE FROM job_requirement WHERE job_id BETWEEN 1 AND 14;

-- 1. 腾讯云智研发 · Java 开发实习生（演示岗：Java/MySQL 命中，Spring Boot/Redis/Docker 缺失）
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(1, 'Java', 'REQUIRED', 30.00),
(1, 'Spring Boot', 'REQUIRED', 25.00),
(1, 'MySQL', 'REQUIRED', 20.00),
(1, 'Redis', 'REQUIRED', 15.00),
(1, 'Docker', 'PREFERRED', 10.00);

-- 2. 字节跳动 · 后端开发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(2, 'Java', 'REQUIRED', 25.00),
(2, '数据结构与算法', 'REQUIRED', 25.00),
(2, 'MySQL', 'REQUIRED', 20.00),
(2, 'Redis', 'REQUIRED', 15.00),
(2, '消息队列', 'PREFERRED', 10.00),
(2, '分布式系统', 'PREFERRED', 5.00);

-- 3. 阿里云 · Java 研发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(3, 'Java', 'REQUIRED', 30.00),
(3, 'Spring', 'REQUIRED', 20.00),
(3, 'MySQL', 'REQUIRED', 20.00),
(3, '分布式系统', 'PREFERRED', 15.00),
(3, '高并发', 'PREFERRED', 15.00);

-- 4. 美团 · 后端开发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(4, 'Java', 'REQUIRED', 25.00),
(4, 'Spring Boot', 'REQUIRED', 20.00),
(4, 'MySQL', 'REQUIRED', 20.00),
(4, 'Redis', 'REQUIRED', 15.00),
(4, '消息队列', 'PREFERRED', 10.00),
(4, 'Linux', 'PREFERRED', 10.00);

-- 5. 华为 · 软件开发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(5, 'Java', 'REQUIRED', 30.00),
(5, '数据结构', 'REQUIRED', 25.00),
(5, 'MySQL', 'PREFERRED', 15.00),
(5, '操作系统', 'REQUIRED', 20.00),
(5, '计算机网络', 'PREFERRED', 10.00);

-- 6. 平安科技 · Java 开发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(6, 'Java', 'REQUIRED', 30.00),
(6, 'Spring Boot', 'REQUIRED', 25.00),
(6, 'MySQL', 'REQUIRED', 20.00),
(6, 'Vue', 'PREFERRED', 15.00),
(6, 'Docker', 'PREFERRED', 10.00);

-- 7. 网易 · 前端开发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(7, 'Vue', 'REQUIRED', 35.00),
(7, 'JavaScript', 'REQUIRED', 30.00),
(7, 'HTML/CSS', 'REQUIRED', 25.00),
(7, '小程序', 'PREFERRED', 10.00);

-- 8. 携程 · 前端开发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(8, 'Vue', 'REQUIRED', 30.00),
(8, 'TypeScript', 'REQUIRED', 25.00),
(8, 'Vite', 'PREFERRED', 15.00),
(8, '移动端适配', 'REQUIRED', 20.00),
(8, 'React', 'PREFERRED', 10.00);

-- 9. 京东 · Web 前端实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(9, 'JavaScript', 'REQUIRED', 30.00),
(9, 'Vue', 'REQUIRED', 25.00),
(9, 'CSS', 'REQUIRED', 20.00),
(9, '性能优化', 'PREFERRED', 15.00),
(9, '响应式设计', 'PREFERRED', 10.00);

-- 10. 荣耀 · 软件测试实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(10, '测试理论', 'REQUIRED', 35.00),
(10, 'Python', 'REQUIRED', 30.00),
(10, 'Linux', 'PREFERRED', 20.00),
(10, '自动化测试', 'PREFERRED', 15.00);

-- 11. 大疆 · 测试开发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(11, 'Python', 'REQUIRED', 35.00),
(11, '测试框架', 'REQUIRED', 25.00),
(11, 'MySQL', 'PREFERRED', 15.00),
(11, 'CI/CD', 'PREFERRED', 15.00),
(11, 'Linux', 'PREFERRED', 10.00);

-- 12. 哔哩哔哩 · 产品经理实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(12, '需求分析', 'REQUIRED', 30.00),
(12, '数据分析', 'REQUIRED', 25.00),
(12, 'Axure', 'REQUIRED', 20.00),
(12, '用户调研', 'PREFERRED', 15.00),
(12, '沟通能力', 'PREFERRED', 10.00);

-- 13. 顺丰科技 · 数据分析实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(13, 'SQL', 'REQUIRED', 35.00),
(13, 'Python', 'REQUIRED', 25.00),
(13, 'Excel', 'REQUIRED', 20.00),
(13, '数据可视化', 'PREFERRED', 20.00);

-- 14. 招商银行 · 运维开发实习生
INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
(14, 'Linux', 'REQUIRED', 30.00),
(14, 'Shell', 'REQUIRED', 25.00),
(14, 'Python', 'REQUIRED', 20.00),
(14, 'Docker', 'PREFERRED', 15.00),
(14, '计算机网络', 'PREFERRED', 10.00);
