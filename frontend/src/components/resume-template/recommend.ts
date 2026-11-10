import { TEMPLATES } from './manifest'
import type { TemplateMeta } from './types'

function scoreTemplate(template: TemplateMeta, target: string): number {
  if (!target) {
    return template.keywords.includes('通用') ? 20 : 0
  }

  let score = 0
  for (const keyword of template.keywords) {
    if (keyword === '通用') {
      score += 5
      continue
    }
    if (target.includes(keyword.toLowerCase())) {
      score += 40
    }
  }
  if (template.post && target.includes(template.post.toLowerCase())) {
    score += 30
  }
  return score
}

/**
 * 按目标岗位关键词推荐模板：
 * - 岗位关键词命中 +40，岗位元数据命中 +30，通用模板 +5；
 * - 优先保证风格多样性（同风格最多 1 套，最后用剩余高分模板补齐）。
 */
export function recommendTemplates(
  targetPosition: string | null | undefined,
  limit = 3,
): TemplateMeta[] {
  const target = (targetPosition ?? '').trim().toLowerCase()
  const ranked = TEMPLATES.map((template) => ({
    template,
    score: scoreTemplate(template, target),
  })).sort(
    (left, right) =>
      right.score - left.score ||
      left.template.name.localeCompare(right.template.name, 'zh-CN'),
  )

  const picked: TemplateMeta[] = []
  for (const item of ranked) {
    if (picked.length >= limit) {
      break
    }
    if (picked.some((template) => template.style === item.template.style)) {
      continue
    }
    picked.push(item.template)
  }

  for (const item of ranked) {
    if (picked.length >= limit) {
      break
    }
    if (!picked.includes(item.template)) {
      picked.push(item.template)
    }
  }

  return picked.slice(0, Math.max(0, limit))
}
