import { describe, expect, it } from 'vitest'

import { recommendTemplates } from './recommend'

describe('template recommendation', () => {
  it('recommends Java/backend templates for a Java role', () => {
    const ids = recommendTemplates('Java 后端开发实习生').map((template) => template.id)
    expect(ids).toContain('67b5e1793370f88ef9258aa6')
  })

  it('recommends new media templates for an operations role', () => {
    const ids = recommendTemplates('新媒体运营实习生').map((template) => template.id)
    expect(ids).toContain('67615d45e4dde1f4d601c8c3')
  })

  it('recommends accounting templates for an accounting role', () => {
    const ids = recommendTemplates('会计校招').map((template) => template.id)
    expect(ids).toContain('67584681ad08c962be2e9da4')
  })

  it('returns three generic templates when there is no target position', () => {
    const templates = recommendTemplates(null)
    expect(templates).toHaveLength(3)
    expect(templates.every((template) => template.keywords.includes('通用'))).toBe(true)
  })

  it('returns at most three and prefers layout diversity', () => {
    const templates = recommendTemplates('Java 后端开发')
    expect(templates).toHaveLength(3)
    expect(new Set(templates.map((template) => template.pageName)).size).toBeGreaterThan(0)
  })
})
