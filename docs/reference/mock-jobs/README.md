# 模拟岗位种子数据（MOCK）

## 来源

- 原始目录：「由 WorkBuddy 会话生成,已完整归档至本目录」
- 归档日期：2026-11-11
- 数据性质：全部为模拟内容，公司名虚构，不对应任何真实招聘。
- 生成方法：人工参考公开岗位的职责/要求常见写法，仅抽取结构骨架与技能词表后自行重写；未复制岗位原文。

## 文件

| 文件 | 用途 |
|---|---|
| `mock-jobs.json` | 结构化数据，作为 `database/seed-jobs.config.json` 的内容源 |
| `mock-jobs.js` | 原始数据源，供 `build.js` 使用 |
| `build.js` | 合规自检脚本，运行 `node build.js` 可重新生成 JSON/词表 |
| `word-bank.json` | 结构骨架与通用技能词表，作为“未复制原文”的留档证据 |
| `index.html` | 数据审核/预览页，不是产品页面 |

最近一次自检结果：

```text
11/11 项通过 | 12 条岗位 | 57 个技能词 | 0 个问题
```

## 与数据库的关系

- 运行时后端从 MySQL 读取 `job` / `job_requirement`，**不会加载本目录的 JS/JSON**。
- `database/seed-jobs.config.json` 把本目录的 12 条岗位映射为数据库 ID `1001~1012`。
- `database/scripts/build-seed-from-mock.mjs` 负责生成审核草案和 `database/seed.sql`。
- 每岗保留 5 个关键技能；其余技能保留在 `jd_content` 的「技能关键词」中。
- `1001` 是演示岗：Spring Boot 原本就在源数据中（`REQUIRED` 25），额外补充 Redis / Docker 两个 `PREFERRED` 差距项；权重 25/25/20/15/15（3R/2P）。
- `1003` 是前端特例：HTML/CSS/JavaScript/Vue 四个基础均为 `REQUIRED`，TypeScript 为 `PREFERRED`，权重 20/20/25/25/10（4R/1P）。
- 其余岗位为 3R/2P，权重 30/25/20/15/10。

## 生成命令

```bash
# 生成审核草案（写 database/seed-jobs-draft.md）
node database/scripts/build-seed-from-mock.mjs

# 预览 SQL（只输出到 stdout，不覆盖 seed.sql）
node database/scripts/build-seed-from-mock.mjs --sql

# 审核通过后写入 database/seed.sql
node database/scripts/build-seed-from-mock.mjs --sql --write
```

## 维护约定

- 不直接手改生成后的 `database/seed.sql`；先改 `database/seed-jobs.config.json`，再重新生成。
- 本目录的 `mock-jobs.json` 是内容源，`build.js` 是合规自检；升级数据时同时保留自检结果。
- 不把 `index.html` 当作产品页面；产品页面是 `frontend/src/views/Jobs/index.vue`。
