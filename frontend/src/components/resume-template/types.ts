export type PageName = 'BasePage' | 'LeftRightPage' | 'Page_1'

export interface TemplateMeta {
  id: string
  name: string
  style: string
  industry: string
  post: string
  pageName: PageName
  file: string
  cover: string
  keywords: string[]
}

export interface TemplateModule {
  id: string
  componentName: string
  title: string
  props: Record<string, { show?: boolean; [key: string]: unknown }>
  css: Record<string, any>
  customCss: Array<{ prop: string; css: Record<string, any>; [key: string]: any }>
  dataSource: Record<string, any>
  customProps: Record<string, any>
  [key: string]: any
}

export interface TemplateJson {
  id: string
  version: string
  componentsTree: TemplateModule[]
  props: { pageName: PageName; title?: string; [key: string]: any }
  css: Record<string, any>
  customCss: any[]
  [key: string]: any
}

export interface ResumeMeta {
  templateId: string
  templateName?: string | null
  generatedAt?: string | null
  source?: string | null
  generationReason?: string | null
  targetJobId?: number | null
  warnings?: string[] | null
}

export interface ResumePersonalInfo {
  realName: string | null
  phone: string | null
  email: string | null
  city: string | null
  avatar: string | null
  school: string | null
  major: string | null
  degree: string | null
  graduationYear: number | null
  yearsOfExperience: number | null
  abstract: string | null
}

export interface ResumeJobIntention {
  targetPosition: string | null
  targetCity: string | null
  expectedSalary: string | null
}

export interface ResumeEducation {
  school: string | null
  major: string | null
  degree: string | null
  graduationYear: number | null
  startDate: string | null
  endDate: string | null
}

export interface ResumeProject {
  projectName: string | null
  role: string | null
  description: string | null
  techStack: string[] | null
  highlights: string[] | null
  startDate: string | null
  endDate: string | null
}

export interface ResumeExperience {
  company: string | null
  position: string | null
  type: 'WORK' | 'INTERNSHIP' | string | null
  description: string | null
  achievements: string | null
  highlights: string[] | null
  startDate: string | null
  endDate: string | null
}

export interface ResumeContent {
  meta: ResumeMeta
  personalInfo: ResumePersonalInfo
  jobIntention: ResumeJobIntention
  education: ResumeEducation[]
  skills: string[]
  projects: ResumeProject[]
  experiences: ResumeExperience[]
  selfEvaluation: string
}
