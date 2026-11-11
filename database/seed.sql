-- ============================================================
-- CareerFlow-AI 模拟职位库种子数据（10~20 条）
-- 依据：docs/requirements.md、docs/development-plan.md D8
-- 前置：先执行 database/init.sql 建库建表。
-- 执行方式（密码交互输入，不写入脚本）：
--   mysql --default-character-set=utf8mb4 -u root -p < database/seed.sql
-- 说明：可重复执行；Windows 客户端必须指定 utf8mb4，否则中文会报 1366。
--       岗位 ID 固定为 1001~1012，source_type=MOCK；
--       每条岗位的技能权重合计 100，便于匹配公式直接使用。
-- ============================================================

USE ai_resume;

SET NAMES utf8mb4;

-- 先清理本次种子岗位的技能要求，保证重复执行不产生重复行。
-- 不删除岗位本身之外的任何数据。
DELETE FROM job_requirement WHERE job_id BETWEEN 1001 AND 1012;

INSERT INTO job
    (id, company, position, city, salary, jd_content, source_type, external_url, created_at, updated_at)
VALUES
    (1001, '星云科技', 'Java 开发实习生', '深圳', '200-300元/天',
     '岗位职责：参与公司业务系统的后端接口开发与联调。任职要求：熟悉 Java 基础与面向对象编程；了解 Spring Boot 框架；熟悉 MySQL 数据库；了解 Redis 缓存；了解 Docker 容器化部署。',
     'MOCK', NULL, NOW(), NOW()),
    (1002, '蓝湖互动', '前端开发实习生', '深圳', '180-260元/天',
     '岗位职责：负责公司 Web 产品的前端页面开发与体验优化。任职要求：熟悉 Vue 3；掌握 TypeScript；熟悉 CSS 布局；使用过 Element Plus；了解 Git 协作流程。',
     'MOCK', NULL, NOW(), NOW()),
    (1003, '云帆网络', 'Java 后端开发校招', '北京', '12-18K/月',
     '岗位职责：参与高并发业务系统设计与开发。任职要求：扎实的 Java 基础；熟悉 Spring Boot；熟悉 MySQL 与索引优化；了解 Redis；熟悉 Linux 常用命令。',
     'MOCK', NULL, NOW(), NOW()),
    (1004, '智核科技', '算法工程师实习生', '上海', '250-400元/天',
     '岗位职责：参与推荐与搜索算法研发。任职要求：熟悉 Python；具备机器学习基础；熟悉 PyTorch；熟练使用 SQL；掌握常用数据结构与算法。',
     'MOCK', NULL, NOW(), NOW()),
    (1005, '稳测软件', '软件测试实习生', '杭州', '150-220元/天',
     '岗位职责：负责 Web 产品的功能测试与接口测试。任职要求：能独立设计测试用例；熟悉接口测试；使用过 Postman；熟悉 SQL；了解自动化测试。',
     'MOCK', NULL, NOW(), NOW()),
    (1006, '橙果互联', '产品经理实习生', '广州', '150-220元/天',
     '岗位职责：参与产品需求调研、原型设计与版本推进。任职要求：具备需求分析能力；能输出原型设计；熟悉 Axure；具备数据分析意识；沟通协调能力强。',
     'MOCK', NULL, NOW(), NOW()),
    (1007, '新媒工场', '新媒体运营实习生', '深圳', '120-180元/天',
     '岗位职责：负责公众号与短视频账号的内容运营。任职要求：文案撰写能力强；了解短视频运营；能进行数据分析；具备排版编辑能力；对热点敏感。',
     'MOCK', NULL, NOW(), NOW()),
    (1008, '数说咨询', '数据分析实习生', '北京', '180-260元/天',
     '岗位职责：参与业务数据整理、分析与可视化。任职要求：熟练使用 SQL；熟悉 Excel；掌握 Python；能使用数据可视化工具；具备统计学基础。',
     'MOCK', NULL, NOW(), NOW()),
    (1009, '一格设计', '交互设计实习生', '上海', '180-260元/天',
     '岗位职责：参与移动端与 Web 端产品交互设计。任职要求：熟悉 Figma；具备交互设计能力；了解用户研究；有视觉设计基础；能输出原型设计。',
     'MOCK', NULL, NOW(), NOW()),
    (1010, '诚正会计', '会计实习生', '杭州', '120-180元/天',
     '岗位职责：协助完成日常账务处理与报表编制。任职要求：熟悉财务核算；熟练使用 Excel；了解税务申报；能编制财务报表；工作细心严谨。',
     'MOCK', NULL, NOW(), NOW()),
    (1011, '海拓贸易', '外贸业务实习生', '成都', '120-180元/天',
     '岗位职责：协助维护海外客户并跟进订单。任职要求：英语听说读写良好；了解国际贸易流程；具备客户沟通能力；熟练使用 Excel；能撰写商务邮件。',
     'MOCK', NULL, NOW(), NOW()),
    (1012, '字里行间', '文案策划实习生', '武汉', '120-180元/天',
     '岗位职责：负责品牌文案与活动内容策划。任职要求：文案撰写能力强；具备活动策划能力；了解新媒体运营；有排版编辑经验；沟通协调能力强。',
     'MOCK', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    company = VALUES(company),
    position = VALUES(position),
    city = VALUES(city),
    salary = VALUES(salary),
    jd_content = VALUES(jd_content),
    source_type = VALUES(source_type),
    external_url = VALUES(external_url),
    created_at = VALUES(created_at),
    updated_at = VALUES(updated_at);

INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES
    (1001, 'Java', 'REQUIRED', 25.00),
    (1001, 'Spring Boot', 'REQUIRED', 25.00),
    (1001, 'MySQL', 'REQUIRED', 20.00),
    (1001, 'Redis', 'PREFERRED', 15.00),
    (1001, 'Docker', 'PREFERRED', 15.00),

    (1002, 'Vue', 'REQUIRED', 30.00),
    (1002, 'TypeScript', 'REQUIRED', 25.00),
    (1002, 'CSS', 'REQUIRED', 15.00),
    (1002, 'Element Plus', 'PREFERRED', 15.00),
    (1002, 'Git', 'PREFERRED', 15.00),

    (1003, 'Java', 'REQUIRED', 30.00),
    (1003, 'Spring Boot', 'REQUIRED', 25.00),
    (1003, 'MySQL', 'REQUIRED', 20.00),
    (1003, 'Redis', 'PREFERRED', 15.00),
    (1003, 'Linux', 'PREFERRED', 10.00),

    (1004, 'Python', 'REQUIRED', 30.00),
    (1004, '机器学习', 'REQUIRED', 25.00),
    (1004, 'PyTorch', 'REQUIRED', 20.00),
    (1004, 'SQL', 'PREFERRED', 15.00),
    (1004, '数据结构', 'PREFERRED', 10.00),

    (1005, '测试用例', 'REQUIRED', 30.00),
    (1005, '接口测试', 'REQUIRED', 25.00),
    (1005, 'Postman', 'PREFERRED', 15.00),
    (1005, 'SQL', 'PREFERRED', 15.00),
    (1005, '自动化测试', 'PREFERRED', 15.00),

    (1006, '需求分析', 'REQUIRED', 30.00),
    (1006, '原型设计', 'REQUIRED', 25.00),
    (1006, 'Axure', 'PREFERRED', 15.00),
    (1006, '数据分析', 'PREFERRED', 15.00),
    (1006, '沟通协调', 'PREFERRED', 15.00),

    (1007, '文案撰写', 'REQUIRED', 30.00),
    (1007, '短视频运营', 'REQUIRED', 25.00),
    (1007, '数据分析', 'PREFERRED', 15.00),
    (1007, '排版编辑', 'PREFERRED', 15.00),
    (1007, '热点敏感度', 'PREFERRED', 15.00),

    (1008, 'SQL', 'REQUIRED', 30.00),
    (1008, 'Excel', 'REQUIRED', 20.00),
    (1008, 'Python', 'REQUIRED', 20.00),
    (1008, '数据可视化', 'PREFERRED', 20.00),
    (1008, '统计学', 'PREFERRED', 10.00),

    (1009, 'Figma', 'REQUIRED', 30.00),
    (1009, '交互设计', 'REQUIRED', 25.00),
    (1009, '用户研究', 'PREFERRED', 20.00),
    (1009, '视觉设计', 'PREFERRED', 15.00),
    (1009, '原型设计', 'PREFERRED', 10.00),

    (1010, '财务核算', 'REQUIRED', 30.00),
    (1010, 'Excel', 'REQUIRED', 25.00),
    (1010, '税务申报', 'PREFERRED', 20.00),
    (1010, '财务报表', 'PREFERRED', 15.00),
    (1010, '细心严谨', 'PREFERRED', 10.00),

    (1011, '英语', 'REQUIRED', 30.00),
    (1011, '国际贸易', 'REQUIRED', 25.00),
    (1011, '客户沟通', 'PREFERRED', 20.00),
    (1011, 'Excel', 'PREFERRED', 15.00),
    (1011, '邮件写作', 'PREFERRED', 10.00),

    (1012, '文案撰写', 'REQUIRED', 30.00),
    (1012, '活动策划', 'REQUIRED', 25.00),
    (1012, '新媒体运营', 'PREFERRED', 20.00),
    (1012, '排版编辑', 'PREFERRED', 15.00),
    (1012, '沟通协调', 'PREFERRED', 10.00);
