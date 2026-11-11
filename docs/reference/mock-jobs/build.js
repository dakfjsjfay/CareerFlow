/*
 * 构建 + 合规自检脚本(零依赖,Node 运行:node build.js)
 *
 * 1) 校验种子数据是否符合 MOCK 约定,任一项不通过就不产出文件
 * 2) 由唯一数据源 mock-jobs.js 生成:
 *      mock-jobs.json   —— 供其他页面 / 后端读取
 *      word-bank.json   —— 只含结构骨架与通用词表,是「不落地原文」的证据
 */

const fs = require("fs");
const path = require("path");

const data = require("./mock-jobs.js");
const jobs = data.jobs;
const raw = JSON.stringify(jobs);

const checks = [];
function check(name, fn) {
  let ok = false;
  try { ok = !!fn(); } catch (e) { ok = false; }
  checks.push({ name, ok });
}

// 这些正则用于「防止误写真实招聘信息」,不是用来筛内容的
const contactRe = /(1[3-9]\d{9}|[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}|https?:\/\/|www\.|QQ群|微信号|联系电话|联系人|简历发送)/i;
const realCompanyRe = /(腾讯|阿里|字节|美团|京东|百度|网易|华为|小米|哔哩哔哩|B站|小红书|快手|滴滴|携程|智联招聘|前程无忧|BOSS直聘|拉勾|猎聘|实习僧|牛客)/i;
const hiringEntryRe = /(投递链接|网申地址|截止日期|HR邮箱|招聘负责人|扫码|二维码)/i;

check("meta.isMock 为 true 且带完整声明", () =>
  data.meta && data.meta.isMock === true && data.meta.notice && data.meta.method);

check("岗位数量不少于 10 条", () => jobs.length >= 10);

check("岗位 id 唯一", () => new Set(jobs.map(j => j.id)).size === jobs.length);

check("公司名唯一(避免成套保留可反查组合)", () =>
  new Set(jobs.map(j => j.company)).size === jobs.length);

check("每条记录都带 isMock / companyIsFictional / mockNotice", () =>
  jobs.every(j => j.isMock === true && j.companyIsFictional === true && !!j.mockNotice));

check("不含真实联系方式或外部链接", () => !contactRe.test(raw));

check("不含真实企业名称", () => !realCompanyRe.test(raw));

check("不含真实招聘入口指向词", () => !hiringEntryRe.test(raw));

check("职责 4-6 条 / 要求 3-5 条 / 加分项 >= 2 / 技能词 >= 3", () =>
  jobs.every(j => j.responsibilities.length >= 4 && j.responsibilities.length <= 6
    && j.requirements.length >= 3 && j.requirements.length <= 5
    && j.bonus.length >= 2 && j.skills.length >= 3));

check("每条含模拟城市、模拟薪资与出勤说明", () =>
  jobs.every(j => j.city && j.salary && j.attendance && j.headcount >= 1));

check("无完全重复的句子(避免模板化复制)", () => {
  const lines = jobs.flatMap(j => [
    j.summary, ...j.responsibilities, ...j.requirements, ...j.bonus
  ]);
  return new Set(lines).size === lines.length;
});

const failed = checks.filter(c => !c.ok);
console.log("MOCK 种子岗位 · 上线前合规自检");
console.log("-".repeat(46));
checks.forEach(c => console.log((c.ok ? "[PASS] " : "[FAIL] ") + c.name));
console.log("-".repeat(46));

if (failed.length > 0) {
  console.log(`结果:${failed.length} 项未通过,已中止生成。`);
  process.exit(1);
}

fs.writeFileSync(
  path.join(__dirname, "mock-jobs.json"),
  JSON.stringify(data, null, 2) + "\n",
  "utf8"
);

const skillWords = [...new Set(jobs.flatMap(j => j.skills))]
  .sort((a, b) => a.localeCompare(b, "zh-CN"));

const wordBank = {
  isMock: true,
  notice: "仅含结构说明与通用词表,不含任何岗位原文句子。",
  skeleton: {
    responsibilities: "4-6 条,按「动作 + 对象 + 交付物」组织",
    requirements: "3-5 条,按「背景 / 硬技能 / 工具 / 软性素质」组织",
    bonus: "2-3 条,按「作品 / 进阶工具 / 相关经历」组织",
    perks: "2-3 条,带教、证明、时间安排",
    meta: "岗位名、类别、公司(虚构)、城市、薪资、出勤、人数"
  },
  verbs: ["参与", "协助", "负责", "跟进", "整理", "核对", "维护", "输出", "撰写", "记录", "排查", "复盘", "沉淀"],
  softSkills: ["沟通协作", "文档意识", "主动同步进展", "做事细致", "能接受反馈", "结论表达克制", "遵守保密要求"],
  skillWords,
  generatedAt: data.meta.generatedAt
};

fs.writeFileSync(
  path.join(__dirname, "word-bank.json"),
  JSON.stringify(wordBank, null, 2) + "\n",
  "utf8"
);

const jsonSize = (fs.statSync(path.join(__dirname, "mock-jobs.json")).size / 1024).toFixed(1);
console.log(`结果:${checks.length}/${checks.length} 项通过 | ${jobs.length} 条岗位 | ${skillWords.length} 个技能词 | 0 个问题`);
console.log(`已写出 mock-jobs.json (${jsonSize} KB)、word-bank.json`);
