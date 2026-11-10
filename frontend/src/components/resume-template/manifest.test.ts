import { describe, expect, it } from 'vitest'

import { TEMPLATES } from './manifest'
import type { TemplateJson } from './types'

const templateModules = import.meta.glob<{ default: TemplateJson }>('./templates/*.json', {
  eager: true,
})

interface LoadedTemplate {
  path: string
  template: TemplateJson
}

function loadedTemplates(): LoadedTemplate[] {
  return Object.entries(templateModules).map(([path, module]) => ({
    path,
    template: module.default,
  }))
}

describe('template manifest', () => {
  it('has 20 unique templates', () => {
    expect(TEMPLATES).toHaveLength(20)
    expect(new Set(TEMPLATES.map((template) => template.id)).size).toBe(20)
    expect(new Set(TEMPLATES.map((template) => template.file)).size).toBe(20)
  })

  it('uses supported page layouts', () => {
    for (const template of TEMPLATES) {
      expect(['BasePage', 'LeftRightPage', 'Page_1']).toContain(template.pageName)
    }
  })

  it('has a vendored JSON file whose page layout matches the manifest', () => {
    const loaded = loadedTemplates()
    expect(loaded).toHaveLength(20)

    for (const template of TEMPLATES) {
      const entry = loaded.find((item) => item.path.endsWith(`/${template.file}`))
      expect(entry, `missing template file ${template.file}`).toBeTruthy()
      expect(entry?.template.props?.pageName).toBe(template.pageName)
      expect(template.keywords.length).toBeGreaterThan(0)
    }
  })

  it('every template has at least one renderable module', () => {
    for (const { template } of loadedTemplates()) {
      expect(template.componentsTree.length).toBeGreaterThan(0)
      for (const module of template.componentsTree) {
        expect(module.componentName).toMatch(
          /^(BaseInfo|JobIntention|EduBackground|SkillSpecialties|CampusExperience|InternshipExperience|WorkExperience|ProjectExperience|Awards|Hobbies|SelfEvaluation|WorksDisplay|Custom|ResumeTitle)_\d+$/,
        )
      }
    }
  })
})
