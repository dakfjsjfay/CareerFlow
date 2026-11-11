# CareerFlow-AI 后端（backend）

技术栈：Java 17 + Spring Boot 3.5 + MyBatis-Plus 3.5.12 + MySQL 8

## 本地启动

1. 复制根目录 `.env.example` 为 `.env`，填入你的 MySQL 密码与 DeepSeek API Key（`.env` 不会进 Git）。
2. 初始化数据库（首次或需要重置种子数据时）：

```bash
cd d:/GitHubCode/CareerFlow
mysql --default-character-set=utf8mb4 -u root -p < database/init.sql
mysql --default-character-set=utf8mb4 -u root -p < database/seed.sql
```

详细说明见 [database/README.md](../database/README.md)。

3. 在 Git Bash 中加载环境变量并启动（Spring Boot 不会自动读取 `.env` 文件，需要 source 一下）：

```bash
cd backend
set -a && source <(tr -d '\r' < ../.env) && set +a
mvn spring-boot:run
```

4. 验证：浏览器或 curl 访问 `http://localhost:8080/api/health`，应返回：

```json
{ "code": 200, "message": "success", "data": { "status": "UP", "service": "careerflow-backend", "database": "UP" } }
```

## 当前状态（D8）

- 已完成：认证、对话建档、证据链、个人档案、完整度、简历生成/版本/PDF/DOCX 导出、岗位中心与 JD 解析。
- 进行中：D9 匹配分析。
- 数据库初始化与种子数据说明见 [database/README.md](../database/README.md)。

## 包结构

```text
com.team.resume
├── ai            DeepSeekClient / AIService / PromptTemplate / AIResponseParser
├── auth          注册、登录、JWT
├── chat          会话与消息
├── common        Result、分页、全局异常
├── config        Spring Security、CORS
├── file          文档上传与 OCR 分析
├── job           岗位中心与 JD 解析（D8）
├── profile       档案、项目/经历、证据、完整度
└── resume        简历生成、版本、DOCX 导出
```
