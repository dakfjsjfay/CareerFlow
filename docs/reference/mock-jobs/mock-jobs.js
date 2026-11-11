/*
 * MOCK 模拟岗位种子数据 —— 唯一数据源
 *
 * 生成方法:人工参考公开岗位「职责 / 要求」的常见写法,
 * 仅抽取结构骨架(职责 4-6 条 / 要求 3-5 条 / 加分项 / 待遇)与通用技能词表,
 * 所有句子均为自行重写,未复制任何岗位原文。
 *
 * 合规声明:公司名全部虚构,城市 / 薪资 / 人数为合理模拟,
 * 不含真实 HR 联系方式、投递入口、外部链接,不对应任何真实招聘。
 *
 * 浏览器:var MOCK_DATA 挂到 window;Node:module.exports 导出同一对象。
 */

var MOCK_DATA = {
  meta: {
    dataset: "模拟岗位种子数据",
    version: "1.0",
    isMock: true,
    notice: "本数据集全部为模拟(MOCK)内容:公司名、城市、薪资、招聘人数均为虚构或合理模拟,不对应任何真实招聘,不含 HR 联系方式与投递入口。",
    method: "人工参考公开岗位的职责/要求常见写法,仅抽取结构骨架与技能词表后自行重写句子;未复制任何岗位原文。",
    generatedAt: "2026-11-11",
    views: ["列表", "详情", "导出 JSON", "页面横幅"]
  },

  jobs: [
    {
      id: "MOCK-JAVA-01",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "Java 开发实习生",
      category: "技术 · 后端",
      company: "云屿网络科技",
      companyIsFictional: true,
      city: "深圳",
      salary: "150–200 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 2,
      summary: "参与内部业务系统的迭代开发,在带教下完成模块编码与自测。",
      responsibilities: [
        "参与内部业务系统的模块开发,按迭代计划完成接口编码与自测",
        "协助维护服务端公共代码,补充基础单元测试与接口说明",
        "配合排查测试环境中的功能异常,记录问题原因与处理方式",
        "参与需求评审,把产品描述拆成可执行的开发任务",
        "整理开发过程中的常见疑问,沉淀到团队文档"
      ],
      requirements: [
        "计算机、软件工程等相关专业在读,能保证每周固定出勤",
        "掌握 Java 语法基础,理解面向对象、集合与异常处理",
        "了解 Spring Boot 与关系型数据库,能编写基础 SQL",
        "会使用 Git 进行分支与提交,能读懂他人代码"
      ],
      bonus: [
        "有课程设计、个人项目或竞赛作品可演示",
        "接触过缓存、消息队列等常见中间件"
      ],
      perks: ["一对一带教", "实习证明", "弹性到岗时间"],
      skills: ["Java", "Spring Boot", "MySQL", "SQL", "Git", "单元测试"]
    },
    {
      id: "MOCK-BACKEND-02",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "后端开发实习生",
      category: "技术 · 后端",
      company: "栖禾信息技术",
      companyIsFictional: true,
      city: "杭州",
      salary: "180–230 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 2,
      summary: "面向服务端接口与数据处理,兼顾功能实现与文档沉淀。",
      responsibilities: [
        "参与服务端接口的设计与实现,配合前端完成联调",
        "协助梳理数据表结构,编写并优化常用查询",
        "编写定时任务与批处理脚本,支撑日常数据同步",
        "观察接口响应情况,协助定位慢请求与报错日志",
        "补充接口文档与部署说明,方便后续交接"
      ],
      requirements: [
        "计算机相关专业在读,具备基础的数据结构与网络知识",
        "至少熟悉一门后端语言(Java / Python / Go 任一)",
        "理解 HTTP 与 RESTful 风格,了解索引与事务的基本概念",
        "有清晰的排错思路,能主动同步进展与风险"
      ],
      bonus: [
        "用过 Linux 命令行与容器基础操作",
        "有开源项目或持续撰写的技术笔记"
      ],
      perks: ["导师带教", "实习证明", "弹性到岗时间"],
      skills: ["Python", "Go", "RESTful", "MySQL", "Redis", "Linux", "Docker"]
    },
    {
      id: "MOCK-FRONTEND-03",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "前端开发实习生",
      category: "技术 · 前端",
      company: "青柠数科",
      companyIsFictional: true,
      city: "广州",
      salary: "150–200 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 2,
      summary: "把设计稿落成可用页面,关注交互细节与异常状态。",
      responsibilities: [
        "按设计稿实现页面与交互,保证常见分辨率下显示正常",
        "参与组件整理与复用,减少重复的样式代码",
        "对接后端接口,处理加载、空数据与错误状态",
        "协助修复样式问题与浏览器兼容差异",
        "记录页面改动范围,配合版本发布"
      ],
      requirements: [
        "掌握 HTML / CSS / JavaScript 基础",
        "了解 Vue 或 React 其中之一,理解组件与状态管理",
        "会用浏览器开发者工具定位样式与脚本问题",
        "做事细致,能对照设计稿逐项自检"
      ],
      bonus: [
        "有可演示的个人作品或课程项目",
        "了解 TypeScript 或构建工具的基本用法"
      ],
      perks: ["导师带教", "实习证明", "弹性到岗时间"],
      skills: ["HTML", "CSS", "JavaScript", "Vue", "React", "TypeScript"]
    },
    {
      id: "MOCK-ALGO-04",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "算法工程师实习生",
      category: "技术 · 算法",
      company: "拾光智能",
      companyIsFictional: true,
      city: "北京",
      salary: "250–320 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 1,
      summary: "围绕数据与模型做实验复现、指标评估与结果整理。",
      responsibilities: [
        "协助整理与清洗训练样本,维护数据版本记录",
        "复现公开方法中的基线实验,输出可对比的结果",
        "参与模型效果评估,按统一指标整理实验报告",
        "协助把零散实验脚本改造成可重复运行的流程",
        "跟踪相关方向的新方法,做简短内部分享"
      ],
      requirements: [
        "计算机、数学、统计等相关专业本科及以上在读",
        "熟悉 Python 与常用数据处理库,掌握基础机器学习理论",
        "了解至少一种深度学习框架,能读懂训练代码",
        "有实验记录习惯,保证结果可复现"
      ],
      bonus: [
        "有竞赛、论文复现或开源实践经历",
        "熟悉 Linux 环境与计算任务提交流程"
      ],
      perks: ["导师带教", "实习证明", "算力资源支持"],
      skills: ["Python", "PyTorch", "机器学习", "数据清洗", "实验评估", "Linux"]
    },
    {
      id: "MOCK-QA-05",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "软件测试实习生",
      category: "技术 · 测试",
      company: "麦浪软件",
      companyIsFictional: true,
      city: "成都",
      salary: "120–160 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 2,
      summary: "从用例编写到缺陷跟踪,保障功能按预期交付。",
      responsibilities: [
        "根据需求文档编写测试用例,覆盖正常与异常场景",
        "执行功能回归,记录缺陷现象与复现步骤",
        "协助维护缺陷台账,跟踪修复状态并做验证",
        "参与接口测试,核对返回字段与状态码",
        "整理测试小结,反馈容易出问题的模块"
      ],
      requirements: [
        "计算机相关专业在读,做事耐心、记录完整",
        "理解软件生命周期与常见测试类型",
        "能写清楚复现步骤,会查看简单日志",
        "了解 SQL 基础查询"
      ],
      bonus: [
        "用过接口调试工具或自动化测试脚本",
        "了解 Python 基础语法"
      ],
      perks: ["导师带教", "实习证明", "双休"],
      skills: ["测试用例", "缺陷管理", "SQL", "Postman", "Python"]
    },
    {
      id: "MOCK-PM-06",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "产品经理实习生",
      category: "产品",
      company: "星野互联",
      companyIsFictional: true,
      city: "上海",
      salary: "160–220 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 1,
      summary: "协助把模糊想法拆成可执行的需求说明并跟进落地。",
      responsibilities: [
        "协助收集用户反馈与竞品信息,整理成需求清单",
        "参与需求讨论,输出原型草图与字段说明",
        "跟进开发排期,记录需求变更与待确认问题",
        "参与功能验收,核对实现与描述是否一致",
        "维护产品文档与使用说明"
      ],
      requirements: [
        "专业不限,逻辑清晰,表达与书写顺畅",
        "能把模糊想法拆成具体步骤,并说明判断依据",
        "熟悉常见文档与表格工具,会画基础流程图",
        "愿意主动沟通,能推动小事闭环"
      ],
      bonus: [
        "有社团项目、课程项目或独立调研经历",
        "习惯拆解常见应用的交互设计"
      ],
      perks: ["导师带教", "实习证明", "参与完整项目周期"],
      skills: ["需求分析", "原型设计", "流程图", "文档撰写", "跨团队沟通"]
    },
    {
      id: "MOCK-SOCIAL-07",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "新媒体运营实习生",
      category: "运营",
      company: "木石文化",
      companyIsFictional: true,
      city: "长沙",
      salary: "120–160 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 2,
      summary: "负责内容选题、撰写与基础数据复盘。",
      responsibilities: [
        "参与内容选题会,按栏目规划撰写与编辑图文",
        "维护账号发布节奏,整理素材与排版模板",
        "记录阅读、互动等基础数据,输出周度小结",
        "协助活动页面的文案校对与上线检查",
        "收集行业热点,整理可复用的选题库"
      ],
      requirements: [
        "文字表达顺畅,能按统一风格改写稿件",
        "熟悉至少一种内容平台的发布规则",
        "会用基础图片或排版工具,注重细节与错别字",
        "能按排期稳定交付"
      ],
      bonus: [
        "有个人账号或社团宣传经验",
        "会做简单的数据图表"
      ],
      perks: ["导师带教", "实习证明", "作品署名"],
      skills: ["内容策划", "文案编辑", "排版", "数据复盘", "选题库"]
    },
    {
      id: "MOCK-DATA-08",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "数据分析实习生",
      category: "数据",
      company: "潮汐数据",
      companyIsFictional: true,
      city: "南京",
      salary: "160–210 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 1,
      summary: "按统一口径取数、做表、给结论。",
      responsibilities: [
        "按口径提取业务数据,核对完整性与异常值",
        "维护日常报表,保证指标定义前后一致",
        "协助做分群与趋势对比,输出结论与图表",
        "参与临时分析需求,先明确问题再动手取数",
        "整理指标字典,减少口径歧义"
      ],
      requirements: [
        "统计、数学、计算机、经管等相关专业在读",
        "熟练使用 SQL,掌握 Excel 或 Python 其一",
        "理解基础统计概念,能区分相关与因果",
        "结论表达克制,会标注数据局限"
      ],
      bonus: [
        "用过 BI 工具搭建可视化看板",
        "有课程报告或竞赛分析作品"
      ],
      perks: ["导师带教", "实习证明", "弹性到岗时间"],
      skills: ["SQL", "Excel", "Python", "指标体系", "可视化", "统计分析"]
    },
    {
      id: "MOCK-UX-09",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "交互设计实习生",
      category: "设计",
      company: "松果设计",
      companyIsFictional: true,
      city: "深圳",
      salary: "150–190 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 1,
      summary: "梳理流程与状态,让页面更好用、更一致。",
      responsibilities: [
        "参与需求梳理,把流程画成页面流转图与线框稿",
        "协助完善组件状态与异常提示,补齐交互说明",
        "根据评审意见迭代方案,记录修改原因",
        "配合开发与测试核对视觉还原效果",
        "整理设计规范,保持同类页面表现一致"
      ],
      requirements: [
        "设计相关专业或具备同等实践经历,能提供作品说明",
        "掌握常用设计工具,理解基础布局与信息层级",
        "能说明每个设计决定背后的理由",
        "接受反馈,愿意反复打磨细节"
      ],
      bonus: [
        "了解前端实现的基本边界",
        "做过可用性观察或小范围用户访谈"
      ],
      perks: ["导师带教", "实习证明", "作品集指导"],
      skills: ["交互原型", "线框图", "设计规范", "Figma", "可用性"]
    },
    {
      id: "MOCK-ACC-10",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "会计实习生",
      category: "财务",
      company: "简册财税",
      companyIsFictional: true,
      city: "苏州",
      salary: "120–150 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 1,
      summary: "从凭证整理到账务归档,练好基础功。",
      responsibilities: [
        "协助整理原始凭证,核对单据与台账是否一致",
        "录入基础账务信息,配合完成月度归档",
        "参与费用报销单据的合规性初检",
        "协助准备审计所需的资料清单",
        "维护文档目录,方便后续查阅"
      ],
      requirements: [
        "会计、财务管理等相关专业在读",
        "了解基础会计准则与借贷记账逻辑",
        "熟练使用 Excel,对数字敏感、复核细致",
        "遵守信息保密要求"
      ],
      bonus: [
        "用过财务软件或已修完相关核心课程",
        "有社团经费管理经历"
      ],
      perks: ["导师带教", "实习证明", "双休"],
      skills: ["凭证整理", "账务录入", "Excel", "费用报销", "档案归档"]
    },
    {
      id: "MOCK-TRADE-11",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "外贸业务实习生",
      category: "业务",
      company: "远帆商贸",
      companyIsFictional: true,
      city: "厦门",
      salary: "130–170 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 2,
      summary: "协助订单、单证与客户资料的日常跟进。",
      responsibilities: [
        "协助整理产品资料与报价表,统一术语与计量单位",
        "跟进询盘记录,维护客户资料的台账",
        "协助核对订单、单据与交期信息",
        "参与展会及平台资料的翻译与校对",
        "汇总周度跟进情况,标注待办事项"
      ],
      requirements: [
        "国际贸易、商务英语等相关专业在读",
        "能读写英文邮件与产品说明",
        "熟悉 Excel,做事有条理、不漏跟事项",
        "了解基本贸易流程与常见单证名称"
      ],
      bonus: [
        "掌握第二外语或熟悉跨境平台操作",
        "有展会协助或社团外联经验"
      ],
      perks: ["导师带教", "实习证明", "外语环境"],
      skills: ["商务英语", "报价表", "单证核对", "Excel", "客户台账"]
    },
    {
      id: "MOCK-COPY-12",
      isMock: true,
      mockNotice: "模拟岗位,不对应任何真实招聘",
      title: "文案策划实习生",
      category: "内容",
      company: "字里行间创意",
      companyIsFictional: true,
      city: "武汉",
      salary: "120–160 元/天",
      attendance: "每周 4 天 / 连续 3 个月",
      headcount: 1,
      summary: "围绕主题写出多种表达,并按反馈打磨。",
      responsibilities: [
        "参与活动主题讨论,撰写标题、短文案与脚本初稿",
        "按品牌语气调整措辞,保持多篇内容风格统一",
        "协助整理案例素材与灵感库",
        "配合设计确认文案长度与排版位置",
        "根据反馈改写,记录被采纳的表达方式"
      ],
      requirements: [
        "中文、广告、新闻等相关专业优先,文字基本功扎实",
        "能就同一卖点写出多个不同角度的版本",
        "会做事实核对,不写无依据的承诺性表述",
        "能接受多轮修改并按节点交稿"
      ],
      bonus: [
        "有校园刊物、社团账号或短内容创作经历",
        "了解基础传播数据指标"
      ],
      perks: ["导师带教", "实习证明", "作品署名"],
      skills: ["文案撰写", "选题策划", "品牌语气", "校对", "脚本"]
    }
  ]
};

var MOCK_JOBS = MOCK_DATA.jobs;

if (typeof module !== "undefined" && module.exports) {
  module.exports = MOCK_DATA;
}
