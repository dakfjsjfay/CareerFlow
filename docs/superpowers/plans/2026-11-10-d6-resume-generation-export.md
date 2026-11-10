# D6/D7 简历生成与导出 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从已确认档案生成结构化简历，接入 20 套 resume-design 模板，支持版本管理与 PDF/DOCX 导出。

**Architecture:** 后端新增 `com.team.resume.resume` 模块：Java 确定性组装事实、AI 只改写自我评价与亮点、版本写入走行锁事务、POI 生成 DOCX。前端新增只读模板渲染层，把 20 份模板 JSON 与 resume-design 渲染组件 vendor 进来，content 经 adapter 映射到模板 `dataSource`；PDF 用浏览器打印，DOCX 走后端接口。

**Tech Stack:** Java 17、Spring Boot 3.5.3、MyBatis-Plus 3.5.12、Apache POI 5.3.0、Vue 3、Vite 5、TypeScript、Element Plus、Vitest。

**Spec:** `docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md`

## Global Constraints

- 只使用 `CONFIRMED` 档案数据；`PENDING` / `REJECTED` 永不进入简历。
- 不新增/修改数据库表或字段；`templateId` 存在 `resume_version.content.meta`。
- 所有 `/api/resumes/**` 接口校验 `user_id`；非本人返回 403，资源不存在返回 404。
- 响应外层统一 `{ code, message, data }`；导出接口返回二进制流。
- Word 导出是 `.docx`，MIME = `application/vnd.openxmlformats-officedocument.wordprocessingml.document`。
- 旧版本只读，不提供 UPDATE/DELETE；换模板/编辑都创建新版本。
- 模板与组件来自 resume-design（MIT），仓库保留 `LICENSE-resume-design.md` 与来源说明。
- 前端不得直接调用 DeepSeek；AI 只经后端 `ai` 包。
- 本机后端测试命令必须带临时 `JAVA_HOME`：
  ```bash
  export JAVA_HOME='C:\Users\zhengjingying\.jdks\corretto-17.0.20.1'
  'C:\Users\zhengjingying\.maven-tools\apache-maven-3.9.14\bin\mvn' -q test
  ```
- 每个任务结束前至少运行该任务指定的测试；最终运行 `npm test`、`npm run build`、后端全量测试。

## Review Focus

这些是 spec 隐含、但单一功能测试容易漏掉的输入/失败模式；对应测试已写入负责它的任务：

1. 只有基础信息、没有任何教育/技能/项目/经历的档案 → 生成仍成功，空章节全部隐藏（Task 2、Task 10）。
2. 项目/经历描述含 `<script>`、`&`、换行 → 预览与 DOCX 中安全转义且换行正确（Task 7、Task 10）。
3. `current_version_id` 指向 v3 时导出 v1 → 必须导出 v1 内容（Task 7）。
4. `LeftRightPage` 模板里某个模块缺少 `customProps.layout` → 默认归左栏，不能消失（Task 11）。
5. 并发生成撞上 `UNIQUE(resume_id, version_number)` → 返回 409 提示重试，不产生重复版本（Task 4）。

---

## File Map

### 后端（`backend/src/main/java/com/team/resume/resume/`）

| 文件 | 职责 |
|---|---|
| `entity/Resume.java` / `entity/ResumeVersion.java` | 两张表实体 |
| `mapper/ResumeMapper.java` / `mapper/ResumeVersionMapper.java` | 基础 CRUD + 行锁/最大版本号查询 |
| `vo/ResumeContent.java` | 简历内容模型（record，含嵌套 record） |
| `vo/ResumeSummaryVO.java` / `ResumeDetailVO.java` / `ResumeVersionVO.java` / `ResumeVersionSummaryVO.java` / `ResumeGenerateResultVO.java` | 接口返回 |
| `dto/CreateResumeRequest.java` / `GenerateResumeRequest.java` / `SaveVersionRequest.java` / `ExportResumeRequest.java` | 接口入参 |
| `service/ResumeContentAssembler.java` | CONFIRMED ProfileVO → ResumeContent |
| `service/ResumeAiService.java` | AI 改写 + FactGuard + 兜底 |
| `service/ResumeVersionService.java` | 版本事务、归属校验 |
| `service/ResumeService.java` | 编排 CRUD / 生成 / 导出 |
| `service/ResumeDocxExporter.java` | POI DOCX |
| `controller/ResumeController.java` | `/api/resumes/**` |

AI 侧新增：`ai/dto/ResumeGenerationResult.java`、`AIResponseParser.parseResumeGeneration`、`AIService.generateResume`、`PromptTemplate.resumeGeneratePrompt`、`resources/prompts/resume-generate-prompt.txt`。

### 前端

| 文件 | 职责 |
|---|---|
| `src/api/resume.ts` | 简历接口与类型 |
| `src/api/request.ts` | 增加 blob 下载解包 |
| `src/composables/useResume.ts` | 简历页状态与动作 |
| `src/components/resume-template/**` | vendor 模板渲染层、adapter、recommend、manifest |
| `src/components/resume/ResumeEditor.vue` | 内容编辑表单 |
| `src/components/resume/TemplateGalleryDialog.vue` | 模板画廊 |
| `src/components/resume/VersionHistoryDrawer.vue` | 版本历史 |
| `src/utils/exportPdf.ts` | 浏览器打印 |
| `src/views/Resume/index.vue` / `print.css` | 简历工作台 |
| `frontend/scripts/vendor-resume-templates.mjs` | 从本地参考仓库复制/重写模板依赖 |

---

## Task 1: 后端简历领域骨架与内容模型

**Files:**
- Create: `backend/src/main/java/com/team/resume/resume/entity/Resume.java`
- Create: `backend/src/main/java/com/team/resume/resume/entity/ResumeVersion.java`
- Create: `backend/src/main/java/com/team/resume/resume/mapper/ResumeMapper.java`
- Create: `backend/src/main/java/com/team/resume/resume/mapper/ResumeVersionMapper.java`
- Create: `backend/src/main/java/com/team/resume/resume/vo/ResumeContent.java`
- Test: `backend/src/test/java/com/team/resume/resume/ResumeContentTest.java`

**Interfaces:**
- Produces:
  ```java
  @Data @TableName("resume")
  public class Resume { Long id; Long userId; String resumeName; Long currentVersionId; LocalDateTime createdAt; LocalDateTime updatedAt; }

  @Data @TableName(value = "resume_version", autoResultMap = true)
  public class ResumeVersion { Long id; Long resumeId; Integer versionNumber;
      @TableField(typeHandler = JacksonTypeHandler.class) JsonNode content;
      String generationReason; LocalDateTime createdAt; }

  public interface ResumeMapper extends BaseMapper<Resume> {
      @Select("SELECT * FROM resume WHERE id = #{id} FOR UPDATE")
      Resume selectByIdForUpdate(@Param("id") Long id);
  }

  public interface ResumeVersionMapper extends BaseMapper<ResumeVersion> {
      @Select("SELECT COALESCE(MAX(version_number), 0) FROM resume_version WHERE resume_id = #{resumeId}")
      int selectMaxVersionNumber(@Param("resumeId") Long resumeId);
  }
  ```
- `ResumeContent` 使用 record，嵌套 `Meta / PersonalInfo / JobIntention / Education / Project / Experience`，字段与 spec §6 完全一致（`Project`/`Experience` 包含 `List<String> highlights`）。

- [ ] **Step 1: 写失败的序列化测试**

```java
class ResumeContentTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundTripsMetaAndHighlights() throws Exception {
        ResumeContent content = new ResumeContent(
            new ResumeContent.Meta("tpl-1", "模板一", "2026-11-10T12:00:00", "AI", "INITIAL", null, List.of()),
            new ResumeContent.PersonalInfo("张同学", "13800000000", null, null, null,
                "深圳大学", "软件工程", "本科", 2028, 0, "简介"),
            new ResumeContent.JobIntention("Java 开发实习生", "深圳", "面议"),
            List.of(new ResumeContent.Education("深圳大学", "软件工程", "本科", 2028, null, null)),
            List.of("Java"),
            List.of(new ResumeContent.Project("学生系统", "后端", "描述", List.of("Java"),
                List.of("亮点"), null, null)),
            List.of(new ResumeContent.Experience("某公司", "实习生", "INTERNSHIP", "描述",
                "成果", List.of("亮点"), null, null)),
            "自我评价");

        JsonNode node = mapper.valueToTree(content);
        ResumeContent back = mapper.treeToValue(node, ResumeContent.class);

        assertEquals("tpl-1", back.meta().templateId());
        assertEquals("亮点", back.projects().get(0).highlights().get(0));
        assertEquals("INTERNSHIP", back.experiences().get(0).type());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `export JAVA_HOME='C:\Users\zhengjingying\.jdks\corretto-17.0.20.1' && 'C:\Users\zhengjingying\.maven-tools\apache-maven-3.9.14\bin\mvn' -q -Dtest=ResumeContentTest test`
Expected: 编译失败，`ResumeContent` 不存在。

- [ ] **Step 3: 实现实体、Mapper、`ResumeContent`**

按上面的签名创建文件；`ResumeContent` 的 record 字段顺序与 spec §6 一致。`generatedAt` 用 `String`，避免时区序列化差异。

- [ ] **Step 4: 运行测试**

Run: 同上
Expected: `BUILD SUCCESS`，1 test passed。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/team/resume/resume backend/src/test/java/com/team/resume/resume/ResumeContentTest.java
git commit -m "feat: add resume entities, mappers and content model"
```

---

## Task 2: ResumeContentAssembler（事实组装）

**Files:**
- Create: `backend/src/main/java/com/team/resume/resume/service/ResumeContentAssembler.java`
- Test: `backend/src/test/java/com/team/resume/resume/ResumeContentAssemblerTest.java`

**Interfaces:**
- Consumes: `ProfileVO`（`profile/vo/ProfileVO.java`）、`ResumeContent`。
- Produces:
  ```java
  @Component
  public class ResumeContentAssembler {
      public ResumeContent assemble(ProfileVO profile, String resumeName, String templateId,
                                    String templateName, String generationReason, Long targetJobId);
      static String fallbackSelfEvaluation(ProfileVO profile);
      static int yearsOfExperience(List<ProfileVO.ExperienceVO> experiences, LocalDate today);
      static String classifyExperience(ProfileVO.ExperienceVO experience); // "WORK" | "INTERNSHIP"
  }
  ```
- 档案完全为空（无姓名、学校/专业、技能、项目、经历）→ `BusinessException(400, "档案信息不足，请先完成对话建档或档案编辑")`。

- [ ] **Step 1: 写失败测试**

至少覆盖：
```java
@Test void classifiesInternshipByKeyword() {
    assertEquals("INTERNSHIP", ResumeContentAssembler.classifyExperience(exp("某公司", "后端实习生")));
    assertEquals("WORK", ResumeContentAssembler.classifyExperience(exp("某公司", "后端工程师")));
}

@Test void computesYearsFromExperienceRanges() {
    var experiences = List.of(expRange("2022-01-01", "2023-01-01"), expRange("2024-01-01", "2025-01-01"));
    assertEquals(2, ResumeContentAssembler.yearsOfExperience(experiences, LocalDate.of(2026, 1, 1)));
}

@Test void mapsProfileWithoutInventingFields() {
    ProfileVO profile = profileWithOnly("张同学", "深圳大学", "软件工程", null, List.of("Java"), List.of(), List.of());
    ResumeContent content = assembler.assemble(profile, "我的简历", "tpl-1", "模板一", "INITIAL", null);
    assertEquals("张同学", content.personalInfo().realName());
    assertNull(content.personalInfo().email());
    assertTrue(content.projects().isEmpty());
}

@Test void rejectsEmptyProfile() {
    assertThrows(BusinessException.class, () -> assembler.assemble(emptyProfile(), "我的简历", "tpl-1", "模板一", "INITIAL", null));
}
```

- [ ] **Step 2: 运行确认失败**

Run: `-Dtest=ResumeContentAssemblerTest`
Expected: 编译失败或测试失败。

- [ ] **Step 3: 实现 assembler**

实现规则：
- `personalInfo` 原样映射；`email/city/avatar` 为 `null`；`yearsOfExperience` 用 `yearsOfExperience(profile.experiences(), LocalDate.now())`。
- `jobIntention` 映射目标岗位/城市/薪资。
- `education`：`school` 或 `major` 非空时生成一条。
- `experiences`：每条 `type = classifyExperience`，`highlights = List.of()`。
- `projects`：原样映射，`techStack` 为 null 时用空列表，`highlights = List.of()`。
- `selfEvaluation` = `fallbackSelfEvaluation(profile)`；`abstract` = 第一句（按 `。！？` 切分）截断 60 字。
- `meta`：templateId/templateName/generationReason/targetJobId，`generatedAt = LocalDateTime.now().toString()`，`source = "AI"`，`warnings = new ArrayList<>()`。

- [ ] **Step 4: 运行测试**

Run: `-Dtest=ResumeContentAssemblerTest`
Expected: 全部通过。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/team/resume/resume/service/ResumeContentAssembler.java backend/src/test/java/com/team/resume/resume/ResumeContentAssemblerTest.java
git commit -m "feat: assemble resume content from confirmed profile"
```

---

## Task 3: AI 简历文案与事实校验

**Files:**
- Create: `backend/src/main/resources/prompts/resume-generate-prompt.txt`
- Create: `backend/src/main/java/com/team/resume/ai/dto/ResumeGenerationResult.java`
- Modify: `backend/src/main/java/com/team/resume/ai/PromptTemplate.java`
- Modify: `backend/src/main/java/com/team/resume/ai/AIResponseParser.java`
- Modify: `backend/src/main/java/com/team/resume/ai/AIService.java`
- Create: `backend/src/main/java/com/team/resume/resume/service/ResumeAiService.java`
- Test: `backend/src/test/java/com/team/resume/resume/ResumeAiServiceTest.java`
- Test: `backend/src/test/java/com/team/resume/ai/ResumeGenerationParserTest.java`

**Interfaces:**
- Produces:
  ```java
  public record ResumeGenerationResult(
      String selfEvaluation,
      List<Highlight> experienceHighlights,
      List<Highlight> projectHighlights) {
      public record Highlight(int index, List<String> highlights) {}
  }

  public String PromptTemplate.resumeGeneratePrompt();
  public ResumeGenerationResult AIResponseParser.parseResumeGeneration(String content);
  public ResumeGenerationResult AIService.generateResume(String sourceJson);

  @Service
  public class ResumeAiService {
      /** AI 调用失败抛 BusinessException(502)；事实校验失败则保留兜底并写 warning。 */
      public ResumeContent enhance(ResumeContent base, ProfileVO profile, List<String> warnings);
  }
  ```

- [ ] **Step 1: 写 Prompt**

`resume-generate-prompt.txt` 必须包含：
- 角色：资深简历顾问。
- 输入：一个 JSON，含 `profile` 与 `baseContent`。
- 输出：**只输出 JSON**，结构
  ```json
  {
    "selfEvaluation": "string",
    "experienceHighlights": [{"index": 0, "highlights": ["string"]}],
    "projectHighlights": [{"index": 0, "highlights": ["string"]}]
  }
  ```
- 硬约束：不得新增输入中不存在的事实、公司、学校、技能、数字；每条亮点必须能在输入原文中找到依据；不确定就返回空数组或空字符串；不要输出 Markdown 代码块。
- 语气：简洁、专业、以动词开头，每条 15-40 字。

- [ ] **Step 2: 写解析测试（失败）**

```java
@Test void parsesSuggestionJson() {
    var r = parser.parseResumeGeneration("{\"selfEvaluation\":\"熟悉 Java\",\"experienceHighlights\":[{\"index\":0,\"highlights\":[\"完成接口开发\"]}],\"projectHighlights\":[]}");
    assertEquals("熟悉 Java", r.selfEvaluation());
    assertEquals("完成接口开发", r.experienceHighlights().get(0).highlights().get(0));
}

@Test void rejectsNonJson() {
    assertThrows(BusinessException.class, () -> parser.parseResumeGeneration("not json"));
}
```

- [ ] **Step 3: 写 `ResumeAiService` 测试（失败）**

用 Mockito mock `AIService`：
```java
@Test void appliesOnlyGroundedHighlights() {
    when(aiService.generateResume(anyString())).thenReturn(new ResumeGenerationResult(
        "熟悉 Java", List.of(new Highlight(0, List.of("完成接口开发", "主导百万用户系统"))), List.of()));
    var warnings = new ArrayList<String>();
    ResumeContent result = service.enhance(base, profile, warnings);
    assertEquals(List.of("完成接口开发"), result.experiences().get(0).highlights());
    assertTrue(warnings.contains("AI_FACT_CHECK_FALLBACK"));
}

@Test void propagatesAiFailure() {
    when(aiService.generateResume(anyString())).thenThrow(new BusinessException(502, "AI 失败"));
    assertThrows(BusinessException.class, () -> service.enhance(base, profile, new ArrayList<>()));
}
```
事实源：把 `profile` 与 `base` 的所有文本拼成一个字符串，用 `FactGuard.isGrounded(source, sentence)` 逐句判断。

- [ ] **Step 4: 实现 PromptTemplate / Parser / AIService / ResumeAiService**

- `PromptTemplate` 构造器加载新 prompt，新增 getter。
- `AIResponseParser.parseResumeGeneration`：解析失败 → `BusinessException(502, "AI 返回格式错误，请稍后重试")`；`selfEvaluation` 允许为空字符串。
- `AIService.generateResume`：messages = system prompt + user sourceJson，`deepSeekClient.completeJson`。
- `ResumeAiService`：用 `ObjectMapper` 构造 `{profile, baseContent}`；调用 AI；逐句 FactGuard；通过的写入 `selfEvaluation` 与对应 `highlights`；`abstract` 更新为自我评价第一句；全部不通过时保留 base 并加 `AI_FACT_CHECK_FALLBACK`。

- [ ] **Step 5: 运行测试**

Run: `-Dtest=ResumeAiServiceTest,ResumeGenerationParserTest,AIServiceTest`
Expected: 全部通过。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/team/resume/ai backend/src/main/java/com/team/resume/resume/service/ResumeAiService.java backend/src/main/resources/prompts/resume-generate-prompt.txt backend/src/test/java/com/team/resume/ai/ResumeGenerationParserTest.java backend/src/test/java/com/team/resume/resume/ResumeAiServiceTest.java
git commit -m "feat: add AI resume enhancement with fact guard"
```

---

## Task 4: 版本服务与事务

**Files:**
- Create: `backend/src/main/java/com/team/resume/resume/vo/ResumeVersionSummaryVO.java`
- Create: `backend/src/main/java/com/team/resume/resume/vo/ResumeVersionVO.java`
- Create: `backend/src/main/java/com/team/resume/resume/service/ResumeVersionService.java`
- Test: `backend/src/test/java/com/team/resume/resume/ResumeVersionServiceTest.java`

**Interfaces:**
- Produces:
  ```java
  @Service
  public class ResumeVersionService {
      @Transactional
      public ResumeVersion create(Long userId, Long resumeId, ResumeContent content, String generationReason);
      public List<ResumeVersionSummaryVO> list(Long userId, Long resumeId);
      public ResumeVersionVO get(Long userId, Long resumeId, Long versionId);
      public ResumeVersion requireOwned(Long userId, Long resumeId, Long versionId);
  }
  ```
  `ResumeVersionSummaryVO(Long versionId, Integer versionNumber, String generationReason, String templateId, LocalDateTime createdAt)`
  `ResumeVersionVO(Long versionId, Integer versionNumber, String generationReason, String templateId, JsonNode content, LocalDateTime createdAt)`

- [ ] **Step 1: 写失败测试**

```java
@Test void createsNextVersionAndUpdatesCurrent() {
    when(resumeMapper.selectByIdForUpdate(1L)).thenReturn(resume(1L, 7L));
    when(versionMapper.selectMaxVersionNumber(1L)).thenReturn(2);
    when(versionMapper.insert(any())).thenAnswer(inv -> { inv.getArgument(0).setId(99L); return 1; });
    ResumeVersion v = service.create(7L, 1L, contentWithTemplate("tpl-1"), "INITIAL");
    assertEquals(3, v.getVersionNumber());
    verify(resumeMapper).updateById(argThat(r -> r.getCurrentVersionId().equals(99L)));
}

@Test void rejectsOtherUsersResume() {
    when(resumeMapper.selectByIdForUpdate(1L)).thenReturn(resume(1L, 8L));
    BusinessException e = assertThrows(BusinessException.class, () -> service.create(7L, 1L, content, "INITIAL"));
    assertEquals(403, e.getCode());
}

@Test void mapsDuplicateVersionNumberTo409() {
    when(versionMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_resume_version"));
    BusinessException e = assertThrows(BusinessException.class, () -> service.create(7L, 1L, content, "INITIAL"));
    assertEquals(409, e.getCode());
}
```

- [ ] **Step 2: 运行确认失败**

Run: `-Dtest=ResumeVersionServiceTest`

- [ ] **Step 3: 实现**

- `create` 先 `selectByIdForUpdate`，为 null → 404；`userId` 不匹配 → 403。
- `versionNumber = max + 1`；`content` 用 `objectMapper.valueToTree` 写入实体。
- `insert` 后设置 `resume.currentVersionId` 并 `updateById`。
- 捕获 `DuplicateKeyException` → `BusinessException(409, "版本冲突，请重试")`。
- `list/get/requireOwned` 都先校验简历归属。

- [ ] **Step 4: 运行测试**

Run: `-Dtest=ResumeVersionServiceTest`
Expected: 全部通过。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/team/resume/resume backend/src/test/java/com/team/resume/resume/ResumeVersionServiceTest.java
git commit -m "feat: add resume version service with locking transaction"
```

---

## Task 5: 简历 CRUD 接口

**Files:**
- Create: `backend/src/main/java/com/team/resume/resume/dto/CreateResumeRequest.java`
- Create: `backend/src/main/java/com/team/resume/resume/vo/ResumeSummaryVO.java`
- Create: `backend/src/main/java/com/team/resume/resume/vo/ResumeDetailVO.java`
- Create: `backend/src/main/java/com/team/resume/resume/service/ResumeService.java`
- Create: `backend/src/main/java/com/team/resume/resume/controller/ResumeController.java`
- Test: `backend/src/test/java/com/team/resume/resume/ResumeServiceTest.java`

**Interfaces:**
- Produces:
  ```java
  public ResumeSummaryVO ResumeService.create(Long userId, CreateResumeRequest request);
  public List<ResumeSummaryVO> ResumeService.list(Long userId);
  public ResumeDetailVO ResumeService.detail(Long userId, Long resumeId);
  public List<ResumeVersionSummaryVO> ResumeService.listVersions(Long userId, Long resumeId);
  public ResumeVersionVO ResumeService.getVersion(Long userId, Long resumeId, Long versionId);
  ```
  `ResumeSummaryVO(Long id, String resumeName, Long currentVersionId, Integer currentVersionNumber, String templateId, LocalDateTime updatedAt)`
  `ResumeDetailVO(Long id, String resumeName, Long currentVersionId, Integer currentVersionNumber, ResumeVersionVO currentVersion)`

- [ ] **Step 1: 写失败测试**

- 创建：`resumeName` 为空时默认「我的简历」；超过 100 字 → 400。
- 列表只返回当前用户数据。
- 详情：非本人 → 403；不存在 → 404。
- 详情 `currentVersion` 为 null 时不报错。

- [ ] **Step 2: 运行确认失败**

Run: `-Dtest=ResumeServiceTest`

- [ ] **Step 3: 实现 Service 与 Controller**

`ResumeService` 构造器注入：`ResumeMapper`、`ResumeVersionMapper`、`ResumeVersionService`、`ResumeContentAssembler`、`ResumeAiService`、`ProfileService`、`ResumeDocxExporter`、`ObjectMapper`。

Controller 签名：
```java
@RestController @RequestMapping("/api/resumes")
public class ResumeController {
    @PostMapping public Result<ResumeSummaryVO> create(@AuthenticationPrincipal Long userId,
        @RequestBody(required = false) CreateResumeRequest request) { ... }
    @GetMapping public Result<List<ResumeSummaryVO>> list(@AuthenticationPrincipal Long userId) { ... }
    @GetMapping("/{id}") public Result<ResumeDetailVO> detail(@AuthenticationPrincipal Long userId,
        @PathVariable Long id) { ... }
    @GetMapping("/{id}/versions") public Result<List<ResumeVersionSummaryVO>> versions(...)
    @GetMapping("/{id}/versions/{versionId}") public Result<ResumeVersionVO> version(...)
}
```
`CreateResumeRequest` 使用 Lombok `@Data`，字段 `String resumeName`；`resumeName` 为空时 Service 置默认值。

- [ ] **Step 4: 运行测试**

Run: `-Dtest=ResumeServiceTest`

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/team/resume/resume backend/src/test/java/com/team/resume/resume/ResumeServiceTest.java
git commit -m "feat: add resume CRUD endpoints"
```

---

## Task 6: 生成与手动保存版本接口

**Files:**
- Create: `backend/src/main/java/com/team/resume/resume/dto/GenerateResumeRequest.java`
- Create: `backend/src/main/java/com/team/resume/resume/dto/SaveVersionRequest.java`
- Create: `backend/src/main/java/com/team/resume/resume/vo/ResumeGenerateResultVO.java`
- Modify: `backend/src/main/java/com/team/resume/resume/service/ResumeService.java`
- Modify: `backend/src/main/java/com/team/resume/resume/controller/ResumeController.java`
- Test: `backend/src/test/java/com/team/resume/resume/ResumeGenerateServiceTest.java`

**Interfaces:**
- Produces:
  ```java
  public ResumeGenerateResultVO ResumeService.generate(Long userId, Long resumeId, GenerateResumeRequest request);
  public ResumeGenerateResultVO ResumeService.saveVersion(Long userId, Long resumeId, SaveVersionRequest request);
  ```
  `GenerateResumeRequest { String templateId; Long targetJobId; String generationReason; }`
  `SaveVersionRequest { JsonNode content; String templateId; String generationReason; }`
  `ResumeGenerateResultVO(Long resumeId, Long versionId, Integer versionNumber, JsonNode content)`

- [ ] **Step 1: 写失败测试**

- `generate`：templateId 为空 → 400；`generationReason` 不在允许集合 → 400；档案为空 → 400；AI 502 时不调用 `versionService.create`。
- `saveVersion`：content 为 null → 400；`templateId` 覆盖 content.meta；`source` 改为 `MANUAL`；创建新版本。
- 旧版本没有被 update（Mockito `verify(versionMapper, never()).updateById(any())`）。

- [ ] **Step 2: 运行确认失败**

Run: `-Dtest=ResumeGenerateServiceTest`

- [ ] **Step 3: 实现**

`generate` 流程：
1. `ResumeVersionService` / `ResumeMapper` 校验简历归属。
2. `profileService.getProfile(userId)`。
3. `assembler.assemble(...)`。
4. `aiService.enhance(content, profile, content.meta().warnings())`。
5. `versionService.create(userId, resumeId, enhanced, reason)`。
6. 返回 `ResumeGenerateResultVO`。

`saveVersion`：
1. 校验简历归属。
2. `objectMapper.treeToValue(request.getContent(), ResumeContent.class)`；解析失败 → 400「简历内容格式错误」。
3. 覆盖 `meta.templateId / generatedAt / source=MANUAL / generationReason`。
4. `versionService.create(...)`。

Controller 增加：
```java
@PostMapping("/{id}/generate") public Result<ResumeGenerateResultVO> generate(...)
@PostMapping("/{id}/versions") public Result<ResumeGenerateResultVO> saveVersion(...)
```

- [ ] **Step 4: 运行测试**

Run: `-Dtest=ResumeGenerateServiceTest`

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/team/resume/resume backend/src/test/java/com/team/resume/resume/ResumeGenerateServiceTest.java
git commit -m "feat: add resume generation and manual version endpoints"
```

---

## Task 7: DOCX 导出

**Files:**
- Create: `backend/src/main/java/com/team/resume/resume/service/ResumeDocxExporter.java`
- Create: `backend/src/main/java/com/team/resume/resume/dto/ExportResumeRequest.java`
- Create: `backend/src/main/java/com/team/resume/resume/vo/ExportFile.java`
- Modify: `backend/src/main/java/com/team/resume/resume/service/ResumeService.java`
- Modify: `backend/src/main/java/com/team/resume/resume/controller/ResumeController.java`
- Test: `backend/src/test/java/com/team/resume/resume/ResumeDocxExporterTest.java`
- Test: `backend/src/test/java/com/team/resume/resume/ResumeExportServiceTest.java`

**Interfaces:**
- Produces:
  ```java
  public byte[] ResumeDocxExporter.export(ResumeContent content, String resumeName, int versionNumber);
  public ExportFile ResumeService.exportDocx(Long userId, Long resumeId, ExportResumeRequest request);
  public record ExportFile(byte[] bytes, String filename) {}
  ```
  `ExportResumeRequest { Long versionId; String format; }`，`format` 仅允许 `DOCX`。

- [ ] **Step 1: 写失败测试**

```java
@Test void exportsReopenableDocx() throws Exception {
    byte[] bytes = exporter.export(sampleContent(), "我的简历", 3);
    assertTrue(bytes.length > 5000);
    assertEquals('P', bytes[0]); assertEquals('K', bytes[1]);
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
        String text = doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(joining("\n"));
        assertTrue(text.contains("张同学"));
        assertTrue(text.contains("项目经历"));
    }
}

@Test void skipsEmptySections() throws Exception {
    byte[] bytes = exporter.export(contentWithoutProjects(), "我的简历", 1);
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
        assertFalse(doc.getParagraphs().stream().anyMatch(p -> p.getText().contains("项目经历")));
    }
}

@Test void escapesHtmlInBullets() throws Exception {
    byte[] bytes = exporter.export(contentWithDescription("<script>alert(1)</script>"), "我的简历", 1);
    // 文本中不能出现 <script> 标签；应显示转义后的 &lt;script&gt; 或纯文本
}
```

Service 测试：
- `format` 非 DOCX → 400。
- versionId 缺失 → 400。
- 版本不属于当前用户 → 403。
- `current_version_id` 是 v3、请求 v1 → 导出 v1 内容（Review Focus 3）。

- [ ] **Step 2: 运行确认失败**

Run: `-Dtest=ResumeDocxExporterTest,ResumeExportServiceTest`

- [ ] **Step 3: 实现**

- `ResumeDocxExporter`：A4、2cm 边距、微软雅黑（`setFontFamily("微软雅黑", XWPFRun.FontCharRange.eastAsia)`），章节顺序见 spec §8.4；空章节跳过；`htmlToText` 把 `<p>` 转换行、去掉标签、反转义实体。
- `ResumeService.exportDocx`：校验归属与 format，取指定版本 content，解析为 `ResumeContent`，调用 exporter；文件名 `sanitize(resumeName) + "-v" + versionNumber + ".docx"`。
- Controller：
  ```java
  @PostMapping("/{id}/export")
  public ResponseEntity<byte[]> export(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                       @Valid @RequestBody ExportResumeRequest request) {
      ExportFile file = resumeService.exportDocx(userId, id, request);
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
          .header(HttpHeaders.CONTENT_DISPOSITION,
              ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
          .body(file.bytes());
  }
  ```

- [ ] **Step 4: 运行测试**

Run: `-Dtest=ResumeDocxExporterTest,ResumeExportServiceTest`

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/team/resume/resume backend/src/test/java/com/team/resume/resume/ResumeDocxExporterTest.java backend/src/test/java/com/team/resume/resume/ResumeExportServiceTest.java
git commit -m "feat: export resume content as docx"
```

---

## Task 8: 后端文档同步与全量验证

**Files:**
- Modify: `docs/api.md` §7
- Modify: `docs/decision-record.md`
- Modify: `docs/architecture.md`
- Modify: `docs/acceptance.md`

- [ ] **Step 1: 更新 `docs/api.md` §7**

写入最终接口、请求/响应示例、`ResumeContent` schema、错误码；导出仅 DOCX；注明 PDF 由前端打印生成。

- [ ] **Step 2: 更新 `docs/decision-record.md`**

新增：
- D17 简历内容模型与模板接入（内容/样式分离、templateId 存 content.meta、换模板=新版本）。
- D18 导出方案调整（PDF 前端打印，DOCX 后端 POI；替代原「PDF = OpenHTMLtoPDF」）。

更新「尚未解决的问题」第 3、4 项为已定稿。

- [ ] **Step 3: 更新 `docs/architecture.md` 与 `docs/acceptance.md`**

补充 resume 模块职责、模板渲染层、导出分工与 D6 验收项。

- [ ] **Step 4: 运行后端全量测试**

Run:
```bash
export JAVA_HOME='C:\Users\zhengjingying\.jdks\corretto-17.0.20.1'
'C:\Users\zhengjingying\.maven-tools\apache-maven-3.9.14\bin\mvn' -q test
```
Expected: `BUILD SUCCESS`，现有测试 + 新增测试全部通过。

- [ ] **Step 5: 提交**

```bash
git add docs/api.md docs/decision-record.md docs/architecture.md docs/acceptance.md
git commit -m "docs: document resume generation, templates and export"
```

---

## Task 9: 前端依赖、模板资源与 vendor 脚本

**Files:**
- Modify: `frontend/package.json`（新增 `sass`、`vue-dompurify-html`）
- Create: `frontend/scripts/vendor-resume-templates.mjs`
- Create: `frontend/src/components/resume-template/templates/*.json`（20 份）
- Create: `frontend/src/components/resume-template/manifest.ts`
- Create: `frontend/src/assets/resume-template/images/`（4 张背景图）
- Create: `frontend/src/assets/resume-template/covers/`（20 张封面）
- Create: `frontend/src/assets/resume-template/people.jpg`
- Create: `frontend/public/static/iconfont/iconfont.js`
- Create: `frontend/src/components/resume-template/vendor/**`（按依赖闭包复制）
- Create: `frontend/src/components/resume-template/LICENSE-resume-design.md`
- Test: `frontend/src/components/resume-template/manifest.test.ts`

**Interfaces:**
- Produces:
  ```ts
  export interface TemplateMeta {
    id: string; name: string; style: string; industry: string; post: string;
    cover: string; pageName: 'BasePage' | 'LeftRightPage' | 'Page_1'; file: string;
    keywords: string[];
  }
  export const TEMPLATES: TemplateMeta[]; // 20 条
  ```

- [ ] **Step 1: 安装依赖**

```bash
cd frontend
npm install --save-dev sass
npm install vue-dompurify-html
```
更新 `package-lock.json`。

- [ ] **Step 2: 写 vendor 脚本**

脚本从 `C:/Users/zhengjingying/Documents/GitHub/_refs/resume-design-dl/resume-design-main` 读取：

1. 复制 20 份模板 JSON（源在 `_refs/template-json/<id>.json`），只保留 `data.template_json`，写到 `src/components/resume-template/templates/<id>.json`。
2. 从 20 份 JSON 的 `componentsTree[].componentName` 出发，解析并递归复制：
   - `src/views/createTemplate/designer/modules/<Type>/<name>.vue`
   - `modules/components/*.vue`、`modules/ModuleTitle/*.vue`
   - `hooks/useGetStyle.ts`、`hooks/useGetCustomStyle.ts`
   - `types/IHJNewSchema.ts`
3. 复制 `src/assets/images/people.jpg` → `src/assets/resume-template/people.jpg`。
4. 复制 `src/assets/createTemplateImages/{baseInfo_2,baseInfo_5,baseInfo_6,title_bg}.png` → `src/assets/resume-template/images/`。
5. 复制 `public/static/iconfont/iconfont.js` → `frontend/public/static/iconfont/iconfont.js`。
6. 复制 `LICENSE` → `LICENSE-resume-design.md`，并在文件头追加来源 URL 与复制日期。
7. 重写复制文件中的导入：
   - `@/views/createTemplate/types/IHJNewSchema` → 相对 `types/IHJNewSchema`
   - `@/utils/common` → 相对 `util`
   - `@/assets/images/people.jpg` → `../../assets/resume-template/people.jpg`
   - `import.meta.glob('/src/assets/createTemplateImages/*')` → `import.meta.glob('../../assets/resume-template/images/*')`
   - 图片 key `/src/assets/createTemplateImages/` → `/src/assets/resume-template/images/`
8. 输出 `src/components/resume-template/vendor/` 下保持原目录结构。

脚本必须幂等：重复运行覆盖同名文件，不删除其他文件。

- [ ] **Step 3: 下载封面并写 manifest**

封面 URL 在 `_refs/template-meta.json` 的 `cover` 字段；下载到 `covers/<id>.png`。manifest 写入 20 条，`keywords` 来自 spec §7.4 的岗位关键词映射。

- [ ] **Step 4: 写 manifest 测试**

```ts
import { TEMPLATES } from './manifest'
import { describe, expect, it } from 'vitest'

describe('template manifest', () => {
  it('has 20 unique templates', () => {
    expect(TEMPLATES).toHaveLength(20)
    expect(new Set(TEMPLATES.map(t => t.id)).size).toBe(20)
  })
  it('uses supported page layouts', () => {
    for (const t of TEMPLATES) {
      expect(['BasePage', 'LeftRightPage', 'Page_1']).toContain(t.pageName)
    }
  })
})
```

- [ ] **Step 5: 运行测试**

Run: `cd frontend && npx vitest run src/components/resume-template/manifest.test.ts`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add frontend/package.json frontend/package-lock.json frontend/scripts frontend/src/components/resume-template frontend/src/assets/resume-template frontend/public/static/iconfont
git commit -m "feat: vendor 20 resume-design templates and assets"
```

---

## Task 10: 模板适配器与推荐

**Files:**
- Create: `frontend/src/components/resume-template/types.ts`
- Create: `frontend/src/components/resume-template/adapter.ts`
- Create: `frontend/src/components/resume-template/recommend.ts`
- Create: `frontend/src/components/resume-template/util.ts`
- Test: `frontend/src/components/resume-template/adapter.test.ts`
- Test: `frontend/src/components/resume-template/recommend.test.ts`

**Interfaces:**
- Produces:
  ```ts
  export interface ResumeContent { meta: {...}; personalInfo: {...}; jobIntention: {...};
    education: EducationItem[]; skills: string[]; projects: ProjectItem[];
    experiences: ExperienceItem[]; selfEvaluation: string }

  export function applyContent(template: TemplateJson, content: ResumeContent, resumeName: string): TemplateJson;
  export function collectRenderedText(template: TemplateJson): string; // 测试/占位断言用
  export function recommendTemplates(targetPosition: string | null, limit = 3): TemplateMeta[];

  // util.ts
  export function formatDate(value: string | [string | null, string | null] | null): string;
  export function escapeHtml(value: string): string;
  export function toParagraphs(values: Array<string | null | undefined>): Array<{ content: { value: string } }>;
  ```

- [ ] **Step 1: 写 adapter 失败测试**

覆盖 Review Focus 1/2：
```ts
it('hides empty list modules', () => {
  const template = loadTemplate('6746fe472f0052cb6a9365aa')
  const result = applyContent(template, contentWithNoProjects(), '我的简历')
  const project = result.componentsTree.find(m => m.componentName.startsWith('ProjectExperience'))!
  expect(project.customProps.showModule).toBe(false)
})

it('escapes html in descriptions', () => {
  const result = applyContent(loadTemplate(id), contentWithDescription('<script>alert(1)</script>'), '我的简历')
  expect(collectRenderedText(result)).not.toContain('<script>')
})

it('never leaks template placeholder data', () => {
  for (const meta of TEMPLATES) {
    const text = collectRenderedText(applyContent(loadTemplate(meta.id), sampleContent, '我的简历'))
    for (const placeholder of ['化简', '小猪课堂', '1584775297@qq.com', '028-83032406']) {
      expect(text).not.toContain(placeholder)
    }
  }
})
```

- [ ] **Step 2: 写 recommend 失败测试**

```ts
it('recommends backend templates for Java roles', () => {
  const ids = recommendTemplates('Java 后端开发实习生').map(t => t.id)
  expect(ids).toContain('67b5e1793370f88ef9258aa6')
})
it('recommends new media template for operations roles', () => {
  expect(recommendTemplates('新媒体运营实习生').map(t => t.id)).toContain('67615d45e4dde1f4d601c8c3')
})
it('returns three generic templates when no target', () => {
  expect(recommendTemplates(null)).toHaveLength(3)
})
```

- [ ] **Step 3: 运行确认失败**

Run: `cd frontend && npx vitest run src/components/resume-template/adapter.test.ts src/components/resume-template/recommend.test.ts`

- [ ] **Step 4: 实现 adapter / recommend / util**

- `applyContent` 深拷贝模板；每个模块按 spec §7.3 映射表重建 `dataSource`；设置 `props.*.show` 与 `customProps.showModule`；列表为空/字段为空时隐藏。
- `jobContent` 用 `toParagraphs([description, achievements, ...highlights, techStack.join('、')])`。
- `collectRenderedText` 递归拼接 `dataSource` 的所有字符串值，供测试断言。
- `recommendTemplates` 按 spec §7.4 打分，保证返回 3 条且布局多样。

- [ ] **Step 5: 运行测试**

Run: 同上
Expected: 全部通过。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/components/resume-template
git commit -m "feat: adapt careerflow content to resume-design templates"
```

---

## Task 11: 只读模板渲染器

**Files:**
- Create: `frontend/src/components/resume-template/types/IHJNewSchema.ts`
- Create: `frontend/src/components/resume-template/hooks/useGetBoxStyle.ts`
- Create: `frontend/src/components/resume-template/hooks/useGetCustomStyle.ts`
- Create: `frontend/src/components/resume-template/SvgIcon.vue`
- Create: `frontend/src/components/resume-template/registerModules.ts`
- Create: `frontend/src/components/resume-template/ReadOnlyModuleBox.vue`
- Create: `frontend/src/components/resume-template/pages/BasePage.vue`
- Create: `frontend/src/components/resume-template/pages/LeftRightPage.vue`
- Create: `frontend/src/components/resume-template/pages/Page_1.vue`
- Create: `frontend/src/components/resume-template/hooks/useGetPageStyle.ts`
- Create: `frontend/src/components/resume-template/ResumeTemplateRenderer.vue`
- Modify: `frontend/src/main.ts`（注册 `vue-dompurify-html`）
- Test: `frontend/src/components/resume-template/ResumeTemplateRenderer.test.ts`

**Interfaces:**
- Consumes: `TemplateJson`、`applyContent`。
- Produces:
  ```ts
  // registerModules.ts
  export const moduleComponents: Record<string, Component>;

  // ResumeTemplateRenderer.vue
  defineProps<{ template: TemplateJson; content: ResumeContent; resumeName: string }>();
  ```
- `ReadOnlyModuleBox.vue` props: `{ module: TemplateModule }`；`customProps.showModule === false` 时 `v-if` 不渲染；`<slot name="module-title">` 保留。
- `LeftRightPage.vue`：`customProps.layout === 'right'` 归右栏，其他（含缺失）归左栏（Review Focus 4）。

- [ ] **Step 1: 写渲染器失败测试**

```ts
it('renders base page template with content', async () => {
  const wrapper = mount(ResumeTemplateRenderer, {
    props: { template: loadTemplate(id), content: sampleContent, resumeName: '我的简历' },
    global: { plugins: [ElementPlus, VueDOMPurifyHTML] },
  })
  expect(wrapper.text()).toContain('张同学')
  expect(wrapper.text()).toContain('Java')
})

it('renders left-right page without losing modules missing layout', async () => {
  const template = loadTemplate(twoColumnId)
  template.componentsTree[0].customProps.layout = undefined
  const wrapper = mount(ResumeTemplateRenderer, { ... })
  expect(wrapper.findAll('.create-template-elemet').length).toBe(template.componentsTree.filter(m => m.customProps.showModule !== false).length)
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/components/resume-template/ResumeTemplateRenderer.test.ts`

- [ ] **Step 3: 实现渲染器**

- 从 vendor 的 `hooks` 简化出 `useGetBoxStyle`（纯 computed）、`useGetCustomStyle`、`useGetPageStyle`（模板 `css` + 左右栏 `customCss` 转内联样式，不做主题联动 watch）。
- `registerModules.ts` 只导入 20 套模板用到的 127 个组件（可由 vendor 脚本生成清单）。
- `main.ts` 增加 `app.use(VueDOMPurifyHTML)`；`SvgIcon` 在 `ResumeTemplateRenderer` 内局部注册，避免污染全局。
- `SvgIcon.vue` 复制原组件；`registerModules` 不含它，作为全局组件在 `ResumeTemplateRenderer` 内局部注册或 `MainLayout` 全局注册。
- `iconfont.js` 用 `loadIconfont()` 只加载一次（`document.createElement('script')`，检查已有 `#resume-iconfont` 防止重复）。
- 页面组件按 `template.props.pageName` 动态选择。

- [ ] **Step 4: 运行测试**

Run: 同上
Expected: 全部通过。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/resume-template
git commit -m "feat: add read-only resume template renderer"
```

---

## Task 12: 前端简历 API 与 composable

**Files:**
- Modify: `frontend/src/api/request.ts`
- Create: `frontend/src/api/resume.ts`
- Create: `frontend/src/composables/useResume.ts`
- Test: `frontend/src/api/resume.test.ts`
- Test: `frontend/src/composables/useResume.test.ts`

**Interfaces:**
- Produces:
  ```ts
  // request.ts
  export interface DownloadResult { blob: Blob; filename: string }

  // resume.ts
  export function createResume(payload?: { resumeName?: string }): Promise<ApiResult<ResumeSummary>>
  export function listResumes(): Promise<ApiResult<ResumeSummary[]>>
  export function getResume(id: number): Promise<ApiResult<ResumeDetail>>
  export function generateResume(id: number, payload: { templateId: string; targetJobId?: number | null; generationReason: string }): Promise<ApiResult<ResumeGenerateResult>>
  export function saveResumeVersion(id: number, payload: { content: ResumeContent; templateId: string; generationReason: string }): Promise<ApiResult<ResumeGenerateResult>>
  export function listResumeVersions(id: number): Promise<ApiResult<ResumeVersionSummary[]>>
  export function getResumeVersion(id: number, versionId: number): Promise<ApiResult<ResumeVersion>>
  export function exportResumeDocx(id: number, versionId: number): Promise<DownloadResult>
  ```
- `useResume` 暴露 `resumes/active/versions/activeVersion/editingContent/loading/generating/saving` 与 `loadResumes/create/generate/selectVersion/saveVersion/exportDocx`。

- [ ] **Step 1: 写失败测试**

- API 路径与 payload 正确（Mock `./request`）。
- blob 响应解包 `{ blob, filename }`（Mock axios response）。
- composable：`generate` 成功后刷新版本并选中新版本；失败时不修改 `active`。

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/api/resume.test.ts src/composables/useResume.test.ts`

- [ ] **Step 3: 实现**

- `request.ts` 响应拦截器：`response.config.responseType === 'blob'` 时返回 `{ blob: response.data, filename }`，filename 从 `content-disposition` 的 `filename*=UTF-8''...` 解析，缺失时用 `resume.docx`。
- `resume.ts` 所有函数用 `request`，导出用 `{ responseType: 'blob' }`。
- `useResume` 用 `ref` 管理状态，错误继续向上抛给页面提示。

- [ ] **Step 4: 运行测试**

Run: 同上
Expected: 全部通过。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/api/request.ts frontend/src/api/resume.ts frontend/src/api/resume.test.ts frontend/src/composables/useResume.ts frontend/src/composables/useResume.test.ts
git commit -m "feat: add resume api client and composable"
```

---

## Task 13: 简历工作台页面

**Files:**
- Create: `frontend/src/components/resume/ResumeEditor.vue`
- Create: `frontend/src/components/resume/TemplateGalleryDialog.vue`
- Create: `frontend/src/components/resume/VersionHistoryDrawer.vue`
- Modify: `frontend/src/views/Resume/index.vue`
- Create: `frontend/src/views/Resume/ResumePage.test.ts`

**Interfaces:**
- Consumes: `useResume`、`ResumeTemplateRenderer`、`TEMPLATES`、`recommendTemplates`、`applyContent`。
- Produces: 页面级组件，无对外 TypeScript 接口。

- [ ] **Step 1: 写页面失败测试**

用 Mock `@/api/resume`，覆盖：
- 无简历时显示「创建我的第一份简历」，点击后调用 `createResume` 并刷新。
- 有版本时右侧渲染预览，包含姓名。
- 点击「AI 生成」→ 选择推荐模板 → 调用 `generateResume`。
- 修改内容后点击保存 → 调用 `saveResumeVersion`，`generationReason === 'MANUAL_EDIT'`。
- 点击「应用此模板」→ `saveResumeVersion`，`generationReason === 'TEMPLATE_SWITCH'`，content 不变。

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/views/Resume/ResumePage.test.ts`

- [ ] **Step 3: 实现页面**

- 顶部工具栏：简历下拉、版本下拉、模板库、AI 生成、保存、导出 PDF、导出 DOCX。
- 左侧 `ResumeEditor`：Element Plus 表单，覆盖 personalInfo/jobIntention/education/skills/projects/experiences/selfEvaluation；数组项可增删。
- 右侧 `ResumeTemplateRenderer`，固定 820px 宽、`transform: scale()` 自适应容器。
- `TemplateGalleryDialog`：推荐区（3 套）+ 全部模板（按 style 分组）；点击即时预览（父组件临时覆盖 `previewTemplate`），「应用此模板」触发 `TEMPLATE_SWITCH`。
- `VersionHistoryDrawer`：版本列表，点击切换预览；行内导出按钮。
- 空状态与错误提示使用 `ElMessage`，与 Profile 页风格一致。

- [ ] **Step 4: 运行测试**

Run: 同上
Expected: 全部通过。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/Resume frontend/src/components/resume
git commit -m "feat: build resume workspace with templates and versions"
```

---

## Task 14: PDF 打印与 DOCX 下载

**Files:**
- Create: `frontend/src/utils/exportPdf.ts`
- Create: `frontend/src/views/Resume/print.css`
- Modify: `frontend/src/views/Resume/index.vue`
- Test: `frontend/src/utils/exportPdf.test.ts`

**Interfaces:**
- Produces: `export function printResume(title: string): void`。

- [ ] **Step 1: 写失败测试**

```ts
it('sets document title before printing and restores it', () => {
  const print = vi.spyOn(window, 'print').mockImplementation(() => {})
  document.title = 'CareerFlow'
  printResume('张同学-v3')
  expect(print).toHaveBeenCalled()
  expect(document.title).toBe('张同学-v3')
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/utils/exportPdf.test.ts`

- [ ] **Step 3: 实现**

- `printResume`：保存旧标题、设置新标题、`window.print()`、监听 `afterprint` 恢复（并加 `setTimeout` 兜底）。
- `print.css` 按 spec §9.3：隐藏非打印区、打印区绝对定位、A4、0.968 缩放、`break-inside: avoid`。
- 页面给预览容器加 `id="resume-print-area"`，导出按钮调用 `printResume(...)`；DOCX 按钮调用 `exportResumeDocx` 并用 `URL.createObjectURL` 触发下载。

- [ ] **Step 4: 运行测试**

Run: 同上
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/utils/exportPdf.ts frontend/src/utils/exportPdf.test.ts frontend/src/views/Resume
git commit -m "feat: add pdf print and docx download"
```

---

## Task 15: 前端文档、全量验证与视觉检查

**Files:**
- Create: `frontend/src/components/resume-template/README.md`
- Create: `frontend/src/components/resume-template/LICENSE-resume-design.md`（若 Task 9 未生成）
- Modify: `docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md`（若实现中产生偏差）

- [ ] **Step 1: 写 vendor README**

说明来源 URL、复制日期、目录结构、如何升级、哪些文件被重写、MIT 归属。

- [ ] **Step 2: 运行前端全量测试**

Run: `cd frontend && npm test`
Expected: 全部通过。

- [ ] **Step 3: 运行构建**

Run: `cd frontend && npm run build`
Expected: `vue-tsc` 与 Vite 构建成功，无类型错误。

- [ ] **Step 4: 运行后端全量测试**

Run:
```bash
export JAVA_HOME='C:\Users\zhengjingying\.jdks\corretto-17.0.20.1'
'C:\Users\zhengjingying\.maven-tools\apache-maven-3.9.14\bin\mvn' -q test
```
Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 手工视觉检查**

启动前后端，使用演示账号：

1. 逐一打开 20 套模板，确认无占位数据、无溢出、无空白标题。
2. 生成一份简历，确认内容只含已确认信息。
3. 切换模板，确认旧版本仍可预览/导出。
4. 打印 PDF（Chrome/Edge）：A4 分页正常、颜色一致。
5. 导出 DOCX，用 Word/WPS 打开：中文、章节、项目符号正常。
6. 用第二个账号访问第一个账号的简历，确认 403。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/components/resume-template/README.md frontend/src/components/resume-template/LICENSE-resume-design.md
git commit -m "docs: add resume-design attribution and vendor notes"
```

---

## Self-Review

- **Spec coverage:** 20 套模板（Task 9）、adapter/recommend（Task 10）、渲染器（Task 11）、后端生成/版本/导出（Task 2-7）、前端页面/PDF/DOCX（Task 13-14）、文档（Task 8/15）均有对应任务。
- **Review Focus:** 五条均已写入对应任务的测试：空档案（Task 2）、HTML 转义（Task 7/10）、旧版本导出（Task 7）、缺 layout（Task 11）、并发版本 409（Task 4）。
- **Type consistency:** 前后端 `ResumeContent` 字段名一致；`templateId` 只在 `meta`；`ResumeGenerateResultVO` 与前端 `ResumeGenerateResult` 对应。
- **Proportion:** 计划只给出签名、测试断言和关键规则，未提前写实现正文。
