import type { TemplateJson } from './types'

const templateModules = import.meta.glob<{ default: TemplateJson }>('./templates/*.json', {
  eager: true,
})

export function loadTemplateById(id: string | null | undefined): TemplateJson | null {
  if (!id) {
    return null
  }
  const module = templateModules[`./templates/${id}.json`]
  return module ? module.default : null
}
