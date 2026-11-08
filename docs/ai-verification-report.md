# DeepSeek API 接入验证报告

> Day 1（2026-11-08）｜执行人：成员 C 任务（Claude Code 代执行）
> 状态：✅ 全部通过

## 1. 验证结论

| 项 | 结果 |
|---|---|
| 模型 ID | `deepseek-v4-pro`（账号权限验证可用） |
| Base URL | https://api.deepseek.com（OpenAI 兼容 /chat/completions） |
| 普通对话 | ✅ HTTP 200，正常回复 |
| JSON Output | ✅ response_format=json_object 生效，返回合法 JSON |
| 多轮对话 | ✅ 客户端维护 messages 历史，模型正确记忆上下文（TXT 案例） |
| 错误密钥 | ✅ HTTP 401 正确拒绝 |
| **归因防幻觉** | ✅ 「学过 MySQL」未被错误归入项目 tech_stack |
| 测试总计 | **通过 5 / 失败 0** |

## 2. 配置说明

- API Key 只从 `.env` 文件或环境变量 `DEEPSEEK_API_KEY` 读取，禁止写入代码、日志、Git
- 模型 ID 写入 `.env` 的 `DEEPSEEK_MODEL`（当前 deepseek-v4-pro），后端 `application.yml` 通过 `${DEEPSEEK_MODEL:deepseek-v4-pro}` 读取
- 测试程序位置：`scratch/DeepSeekTest.java`（已 gitignore，不进仓库；完整代码见下）

## 3. 测试程序

运行方式（JDK 17+ 单文件源码运行，零第三方依赖，仅用 JDK 自带 HttpClient）：

```bash
cd scratch
java DeepSeekTest.java ../.env
```

程序读取 `.env`（支持直接传文件路径参数，Windows 下无需手动 export），5 个测试用例：普通对话 / JSON Output / 多轮记忆 / 错误密钥 401 / 归因防幻觉。

完整代码见 `scratch/DeepSeekTest.java`（D13 测试报告引用，团队成员可复跑）。

## 4. 已知问题

1. **Windows 控制台中文乱码**：程序输出 UTF-8，cmd/PowerShell 默认 GBK 会乱码。解决：在 VSCode 终端运行，或 `java -Dfile.encoding=UTF-8 DeepSeekTest.java ../.env`。不影响 API 调用本身。
2. **模型 ID 需以账号权限为准**：`deepseek-v4-pro` 当前账号可用；若后续换账号，改 `.env` 的 `DEEPSEEK_MODEL` 即可，代码零改动。
3. 测试 5 的判定方式为「返回中不含 "MySQL" 字样」的粗校验；生产环境（D3 起）由 Java 后端做**结构校验 + 事实校验**两层校验（见 docs/architecture.md §4），不能依赖模型自觉。

## 5. Java 集成建议（D3 落地 ai 包时参考）

1. **HTTP 客户端**：优先 JDK 自带 `HttpClient`（已验证可行，零依赖）；也可用 Spring 的 RestClient
2. **超时策略**：连接超时 30s、请求超时 120s（本测试所用值），后端封装时按 architecture.md 定 60s 超时 + 1 次重试 + 失败 502
3. **多轮上下文**：DeepSeek 无服务端会话记忆，必须由客户端维护完整 messages 历史——正好对应 chat_message 表：每次调用前查该会话最近 N 条消息组装 messages
4. **JSON 解析**：response_format=json_object 保证输出是 JSON 字符串，但**格式合法 ≠ 内容正确**：后端仍需①结构校验（字段/枚举/类型）②事实校验（与用户原话对照，防编造）
5. **密钥管理**：仅存 `.env`；Docker Compose 阶段用 env_file 注入容器环境变量

## 6. 对应决策记录

- decision-record D13（AI 接入）确认落地
- decision-record 待解决 #1（模型 ID）已解决
