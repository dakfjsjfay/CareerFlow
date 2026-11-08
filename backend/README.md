# CareerFlow-AI 后端（backend）

技术栈：Java 17 + Spring Boot 3.5 + MyBatis-Plus 3.5.12 + MySQL 8

## 本地启动

1. 复制根目录 `.env.example` 为 `.env`，填入你的 MySQL 密码与 DeepSeek API Key（`.env` 不会进 Git）
2. 在 Git Bash 中加载环境变量并启动（Spring Boot 不会自动读取 .env 文件，需要 source 一下）：

```bash
cd backend
set -a && source ../.env && set +a
mvn spring-boot:run
```

3. 验证：浏览器或 curl 访问 `http://localhost:8080/api/health`，应返回：

```json
{ "code": 200, "message": "success", "data": { "status": "UP", "service": "careerflow-backend" } }
```

## 当前状态（Day 1）

- 仅工程骨架：统一响应 Result、全局异常处理、健康检查、跨域配置
- 业务表与业务接口从 D2 起按 docs/database.md、docs/api.md 开发
- MyBatis-Plus 仅集成依赖与配置，尚未建 Mapper

## 包结构

```
com.team.resume
├── common       统一响应、异常处理、工具
├── config       全局配置
├── controller   接口层（D2 起按业务模块拆分）
├── mapper       数据库访问（D2）
├── service      业务逻辑（D2）
├── entity       数据库实体（D2）
├── dto / vo     请求/返回对象（D2）
└── ai           AI 服务层（D3 接入 DeepSeek）
```
