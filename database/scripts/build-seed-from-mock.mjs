#!/usr/bin/env node
/**
 * 从归档的模拟岗位数据生成审核草案，审核通过后再生成 seed.sql。
 *
 * 用法：
 *   node database/scripts/build-seed-from-mock.mjs
 *     -> 写 database/seed-jobs-draft.md
 *   node database/scripts/build-seed-from-mock.mjs --sql
 *     -> 输出 SQL 到 stdout
 *   node database/scripts/build-seed-from-mock.mjs --sql --write
 *     -> 写 database/seed.sql（审核通过后使用）
 *
 * 数据源：docs/reference/mock-jobs/mock-jobs.json
 * 映射配置：database/seed-jobs.config.json
 */
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const configPath = path.join(root, 'database/seed-jobs.config.json')
const draftPath = path.join(root, 'database/seed-jobs-draft.md')
const sqlPath = path.join(root, 'database/seed.sql')

const config = JSON.parse(await readFile(configPath, 'utf8'))
const source = JSON.parse(await readFile(path.join(root, config.source), 'utf8'))

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

const sourceJobs = new Map(source.jobs.map((job) => [job.id, job]))
assert(sourceJobs.size === source.jobs.length, '源数据岗位 ID 重复')

const prepared = config.jobs.map((item) => {
  const job = sourceJobs.get(item.sourceId)
  assert(job, `找不到源岗位 ${item.sourceId}`)
  assert(job.isMock === true, `源岗位 ${item.sourceId} 缺少 isMock=true`)
  assert(Number.isInteger(item.id), `岗位 ${item.sourceId} 的数据库 ID 必须是整数`)
  assert(item.skills?.length === 5, `岗位 ${item.id} 必须保留 5 个关键技能`)

  const skillNames = new Set()
  let total = 0
  for (const skill of item.skills) {
    assert(skill.skill && typeof skill.skill === 'string', `岗位 ${item.id} 存在空技能`)
    assert(!skillNames.has(skill.skill), `岗位 ${item.id} 技能重复：${skill.skill}`)
    skillNames.add(skill.skill)
    assert(
      ['REQUIRED', 'PREFERRED'].includes(skill.importance),
      `岗位 ${item.id} importance 非法：${skill.importance}`,
    )
    assert(
      typeof skill.weight === 'number' && skill.weight >= 0,
      `岗位 ${item.id} 权重非法：${skill.weight}`,
    )
    total += skill.weight
  }
  assert(Math.abs(total - 100) < 0.001, `岗位 ${item.id} 权重合计 ${total}，应为 100`)

  const bonus = [...(job.bonus ?? []), ...(item.jdOverrides?.bonusAdd ?? [])]
  const skillKeywords = [
    ...new Set([...(job.skills ?? []), ...(item.jdOverrides?.skillKeywordsAdd ?? [])]),
  ]

  return {
    id: item.id,
    sourceId: item.sourceId,
    note: item.note ?? null,
    job,
    skills: item.skills,
    jdContent: buildJd(job, bonus, skillKeywords),
  }
})

assert(prepared.length >= 10 && prepared.length <= 20, '岗位数量应为 10~20')
assert(prepared.length === source.jobs.length, '映射岗位数量应与源数据一致')
assert(new Set(prepared.map((item) => item.id)).size === prepared.length, '数据库岗位 ID 重复')
prepared.sort((a, b) => a.id - b.id)

function buildJd(job, bonus, skillKeywords) {
  return [
    job.mockNotice ?? '模拟岗位，不对应任何真实招聘',
    `岗位方向：${job.category}`,
    `实习安排：${job.attendance}；招聘人数：${job.headcount}`,
    '',
    '【岗位摘要】',
    job.summary,
    '',
    '【岗位职责】',
    ...job.responsibilities.map((text, index) => `${index + 1}. ${text}`),
    '',
    '【任职要求】',
    ...job.requirements.map((text, index) => `${index + 1}. ${text}`),
    '',
    '【加分项】',
    ...bonus.map((text, index) => `${index + 1}. ${text}`),
    '',
    '【实习福利】',
    job.perks.join('、'),
    '',
    '【技能关键词】',
    skillKeywords.join('、'),
  ].join('\n')
}

function renderDraft() {
  const lines = [
    '# 模拟岗位种子数据审核草案',
    '',
    `> 来源：\`${config.source}\`（MOCK，非真实招聘）。`,
    `> 生成日期：${source.meta.generatedAt}；映射：\`1001~1012\`。`,
    '> 规则：每岗保留 5 个关键技能；`1001` 补充 Redis/Docker 作为加分项。',
    '> 特殊权重：`1001` = 25/25/20/15/15（3R/2P）；`1003` = 20/20/25/25/10（4R/1P，前端四大基础均为 REQUIRED）；其余岗位 = 30/25/20/15/10（3R/2P）。',
    '> 本文件由 `database/scripts/build-seed-from-mock.mjs` 生成，审核通过后再写入 `database/seed.sql`。',
    '',
    '## 审核重点',
    '',
    '1. `1001` 是否为深圳 Java 演示岗，Redis/Docker 是否为 PREFERRED。',
    '2. `1003` 是否保持 4 个 REQUIRED（HTML/CSS/JavaScript/Vue）+ TypeScript PREFERRED。',
    '3. 每个岗位的 5 个关键技能是否合理，其余技能是否已保留在 JD 正文。',
    '4. 每个岗位权重是否合计 100。',
    '5. 公司名、城市、薪资是否符合 MOCK 约定。',
    '',
    '## 总览',
    '',
    '| ID | 源 ID | 公司 | 岗位 | 城市 | 薪资 | 关键技能 | 重要性 | 权重 |',
    '|---|---|---|---|---|---|---|---|---|',
    ...prepared.map((item) => {
      const job = item.job
      const required = item.skills.filter((skill) => skill.importance === 'REQUIRED').length
      const preferred = item.skills.length - required
      const weights = item.skills.map((skill) => skill.weight.toFixed(2)).join(' / ')
      return `| ${item.id} | ${item.sourceId} | ${job.company} | ${job.title} | ${job.city} | ${job.salary} | ${item.skills
        .map((skill) => skill.skill)
        .join('、')} | ${required}R/${preferred}P | ${weights} |`
    }),
    '',
    '## 逐岗详情',
  ]

  for (const item of prepared) {
    const job = item.job
    lines.push('', `### ${item.id} · ${job.title}`, '')
    lines.push(`- 源 ID：\`${item.sourceId}\``)
    lines.push(`- 公司：${job.company}（虚构）`)
    lines.push(`- 城市 / 薪资：${job.city} / ${job.salary}`)
    lines.push(`- 岗位方向：${job.category}`)
    lines.push(`- 实习安排：${job.attendance}；招聘人数：${job.headcount}`)
    if (item.note) {
      lines.push(`- 审核备注：${item.note}`)
    }
    lines.push('', '**关键技能**', '', '| 技能 | 重要性 | 权重 |', '|---|---|---|')
    for (const skill of item.skills) {
      lines.push(`| ${skill.skill} | ${skill.importance} | ${skill.weight.toFixed(2)} |`)
    }
    lines.push('', '**JD 正文**', '', '```text', item.jdContent, '```')
  }

  return `${lines.join('\n')}\n`
}

function sqlString(value) {
  return `'${String(value ?? '')
    .replaceAll('\\', '\\\\')
    .replaceAll("'", "''")}'`
}

function renderSql() {
  const lines = [
    '-- ============================================================',
    '-- CareerFlow-AI 模拟职位库种子数据（由 mock-jobs.json 生成）',
    '-- 来源：docs/reference/mock-jobs/mock-jobs.json',
    '-- 生成：database/scripts/build-seed-from-mock.mjs',
    '-- 执行方式：mysql --default-character-set=utf8mb4 -u root -p < database/seed.sql',
    '-- 说明：全部为 MOCK 数据，公司名虚构，不对应任何真实招聘。',
    '-- ============================================================',
    '',
    'USE ai_resume;',
    '',
    'SET NAMES utf8mb4;',
    '',
    'DELETE FROM job_requirement WHERE job_id BETWEEN 1001 AND 1012;',
    '',
    'INSERT INTO job',
    '    (id, company, position, city, salary, jd_content, source_type, external_url, created_at, updated_at)',
    'VALUES',
  ]

  const jobRows = prepared.map((item) => {
    const job = item.job
    return `    (${item.id}, ${sqlString(job.company)}, ${sqlString(job.title)}, ${sqlString(
      job.city,
    )}, ${sqlString(job.salary)}, ${sqlString(item.jdContent)}, 'MOCK', NULL, NOW(), NOW())`
  })
  lines.push(jobRows.join(',\n'))
  lines.push('ON DUPLICATE KEY UPDATE')
  lines.push('    company = VALUES(company),')
  lines.push('    position = VALUES(position),')
  lines.push('    city = VALUES(city),')
  lines.push('    salary = VALUES(salary),')
  lines.push('    jd_content = VALUES(jd_content),')
  lines.push('    source_type = VALUES(source_type),')
  lines.push('    external_url = VALUES(external_url),')
  lines.push('    updated_at = VALUES(updated_at);')
  lines.push('')

  const requirementRows = []
  for (const item of prepared) {
    for (const skill of item.skills) {
      requirementRows.push(
        `    (${item.id}, ${sqlString(skill.skill)}, ${sqlString(skill.importance)}, ${skill.weight.toFixed(
          2,
        )})`,
      )
    }
  }
  lines.push('INSERT INTO job_requirement (job_id, skill, importance, weight) VALUES')
  lines.push(requirementRows.join(',\n') + ';')

  return `${lines.join('\n')}\n`
}

if (process.argv.includes('--sql')) {
  const sql = renderSql()
  if (process.argv.includes('--write')) {
    await writeFile(sqlPath, sql, 'utf8')
    console.log(`wrote ${path.relative(root, sqlPath)}`)
  } else {
    process.stdout.write(sql)
  }
} else {
  await writeFile(draftPath, renderDraft(), 'utf8')
  const requirementCount = prepared.reduce((sum, item) => sum + item.skills.length, 0)
  console.log(`wrote ${path.relative(root, draftPath)}`)
  console.log(`validated ${prepared.length} jobs / ${requirementCount} requirements`)
}
