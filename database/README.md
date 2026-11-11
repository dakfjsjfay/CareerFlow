# 数据库初始化与种子数据

## 1. 前置条件

- MySQL 8 已启动（Windows 服务名通常为 `MySQL80`）。
- 使用 Git Bash、CMD 或 PowerShell 均可。
- 根目录 `.env` 已由 `.env.example` 复制，并填好本地 MySQL 密码与 DeepSeek Key。
- `.env` 已被 `.gitignore` 排除，**禁止提交**。

## 2. 初始化表结构

```bash
cd d:/GitHubCode/CareerFlow
mysql --default-character-set=utf8mb4 -u root -p < database/init.sql
```

执行后终端会提示输入密码，密码不会出现在命令、脚本或 Shell 历史中。

说明：

- `--default-character-set=utf8mb4` 是 Windows 客户端处理中文所必需的，否则可能报 `1366 Incorrect string value`。
- `init.sql` 可重复执行，不会删除已有业务数据。

## 3. 导入模拟岗位

```bash
mysql --default-character-set=utf8mb4 -u root -p < database/seed.sql
```

- 依赖 `init.sql` 已创建 `job` / `job_requirement` 表。
- 固定写入 12 条 MOCK 岗位（ID `1001~1012`）和 60 条技能要求。
- 每次执行会先删除这 12 个岗位的技能要求再重建，可重复执行；不会影响其他岗位。

## 4. 验证

```bash
mysql --default-character-set=utf8mb4 -u root -p -e "
  USE ai_resume;
  SELECT COUNT(*) AS jobs FROM job;
  SELECT COUNT(*) AS requirements FROM job_requirement;
  SELECT job_id, SUM(weight) AS total_weight
  FROM job_requirement
  GROUP BY job_id;
"
```

预期：

- `jobs = 12`
- `requirements = 60`
- 每个 `job_id` 的 `total_weight = 100.00`

## 5. 可选：mysql_config_editor（仅个人开发加速）

如果经常重建本地库，可以配置一次登录路径，后续不再输入密码：

```bash
mysql_config_editor set --login-path=careerflow --host=localhost --user=root --password
mysql --login-path=careerflow --default-character-set=utf8mb4 < database/init.sql
mysql --login-path=careerflow --default-character-set=utf8mb4 < database/seed.sql
```

注意：

- 该配置只对当前电脑当前用户生效，队友和评委需要各自配置。
- 不适合 Docker Compose 或无人值守部署。
- 比赛文档的**官方标准流程仍然是交互式 `-p`**。

## 6. 安全约定

- 不要把密码写进 SQL、Shell 脚本、`application.yml` 或命令参数。
- 不要使用 `mysql -p<密码>`，也不建议使用 `MYSQL_PWD` 环境变量。
- `.env` 只存在本地，`.env.example` 只放占位符。
- 如果密钥曾进入 Git 历史，修改 `.env` **不会**让旧密钥失效；必须在服务商控制台删除/轮换旧密钥。
- 仓库公开或提交比赛材料前，确认旧密钥已失效；必要时再清理 Git 历史。
