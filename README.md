# CareerFlow-AI 智能求职助手

智能应用开发大赛参赛作品 —— 赛题 3/C：自然语言对话式简历生成与精准投放系统。

**产品理念**：不只是生成简历，而是帮助用户持续提高求职匹配度 —— 从简历生成到智能求职闭环。

## 业务闭环

```
① 自然语言对话建档（多轮对话 · 信息抽取 · 缺失追问）
② AI 简历生成（结构化档案 · 版本管理 · PDF/Word 导出）
③ JD 岗位精准匹配（岗位解析 · 技能矩阵 · 可解释评分）
④ AI 反向优化（差距发现 · 追问补充 · 真实经历改写 · 版本迭代）
⑤ 投递管理与反馈（模拟投递 · 状态追踪 · 主动提醒 · 反馈回流）
                        ↑ 反馈回流，驱动下一轮优化
```

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + Pinia + Axios + ECharts |
| 后端 | Java 17 + Spring Boot 3 + MyBatis-Plus + JWT |
| 数据库 | MySQL 8 |
| AI | DeepSeek V4 API（仅后端调用） |
| 部署 | Docker Compose |

## 目录结构

```
├── frontend/    # Vue 前端
├── backend/     # Spring Boot 后端（模块化单体）
├── database/    # init.sql 建表 / seed.sql 模拟岗位数据
├── docs/        # 设计文档（开发唯一依据）
│   ├── requirements.md        需求与 MVP 范围
│   ├── architecture.md        系统架构
│   ├── database.md            数据库设计（17 表 + 状态机 + 提醒规则）
│   ├── api.md                 API 接口契约
│   ├── acceptance.md          功能验收标准
│   ├── development-plan.md    分工与 14 天排期（纵向切片）
│   └── decision-record.md     决策记录与已知限制
└── CLAUDE.md    # 项目开发规范
```

## 三个技术亮点

1. **简历智能完整度雷达**：教育/技能/项目/经历等维度完整程度，补充后自动更新，缺失信息自动追问
2. **JD 差距可视化与证据追溯**：每项岗位技能都能看到匹配状态、原始经历证据与 AI 判断理由，点击差距项直接发起追问
3. **简历版本与投递反馈分析**：每次生成保留版本，投递关联版本，反馈统计驱动下一轮优化

## 快速开始

见 docs/development-plan.md 排期。Day 1 工程初始化完成后在此补充启动命令。
