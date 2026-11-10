import type {
  ExperiencePayload,
  ProfileData,
  ProfileExperience,
  ProfileProject,
  ProfileUpdatePayload,
  ProjectPayload,
} from '@/api/profile'

export interface ProfileFormState {
  realName: string
  phone: string
  school: string
  major: string
  degree: string
  graduationYear: number | null
  skills: string[]
  targetPosition: string
  targetCity: string
  expectedSalary: string
}

export interface ProjectFormState {
  projectName: string
  role: string
  description: string
  techStack: string[]
  startDate: string
  endDate: string
}

export interface ExperienceFormState {
  company: string
  position: string
  description: string
  achievements: string
  startDate: string
  endDate: string
}

export function emptyProfileForm(): ProfileFormState {
  return {
    realName: '',
    phone: '',
    school: '',
    major: '',
    degree: '',
    graduationYear: null,
    skills: [],
    targetPosition: '',
    targetCity: '',
    expectedSalary: '',
  }
}

export function emptyProjectForm(): ProjectFormState {
  return {
    projectName: '',
    role: '',
    description: '',
    techStack: [],
    startDate: '',
    endDate: '',
  }
}

export function emptyExperienceForm(): ExperienceFormState {
  return {
    company: '',
    position: '',
    description: '',
    achievements: '',
    startDate: '',
    endDate: '',
  }
}

export function profileToForm(profile: ProfileData | null): ProfileFormState {
  if (!profile) {
    return emptyProfileForm()
  }
  return {
    realName: profile.realName ?? '',
    phone: profile.phone ?? '',
    school: profile.school ?? '',
    major: profile.major ?? '',
    degree: profile.degree ?? '',
    graduationYear: profile.graduationYear ?? null,
    skills: [...(profile.skills ?? [])],
    targetPosition: profile.targetPosition ?? '',
    targetCity: profile.targetCity ?? '',
    expectedSalary: profile.expectedSalary ?? '',
  }
}

export function formToProfilePayload(form: ProfileFormState): ProfileUpdatePayload {
  return {
    realName: form.realName.trim(),
    phone: form.phone.trim(),
    school: form.school.trim(),
    major: form.major.trim(),
    degree: form.degree.trim(),
    graduationYear: form.graduationYear,
    skills: normalizeList(form.skills),
    targetPosition: form.targetPosition.trim(),
    targetCity: form.targetCity.trim(),
    expectedSalary: form.expectedSalary.trim(),
  }
}

export function projectToForm(project: ProfileProject | null): ProjectFormState {
  if (!project) {
    return emptyProjectForm()
  }
  return {
    projectName: project.projectName ?? '',
    role: project.role ?? '',
    description: project.description ?? '',
    techStack: [...(project.techStack ?? [])],
    startDate: project.startDate ?? '',
    endDate: project.endDate ?? '',
  }
}

export function formToProjectPayload(form: ProjectFormState): ProjectPayload {
  return {
    projectName: form.projectName.trim(),
    role: form.role.trim(),
    description: form.description.trim(),
    techStack: normalizeList(form.techStack),
    startDate: form.startDate || null,
    endDate: form.endDate || null,
  }
}

export function experienceToForm(experience: ProfileExperience | null): ExperienceFormState {
  if (!experience) {
    return emptyExperienceForm()
  }
  return {
    company: experience.company ?? '',
    position: experience.position ?? '',
    description: experience.description ?? '',
    achievements: experience.achievements ?? '',
    startDate: experience.startDate ?? '',
    endDate: experience.endDate ?? '',
  }
}

export function formToExperiencePayload(form: ExperienceFormState): ExperiencePayload {
  return {
    company: form.company.trim(),
    position: form.position.trim(),
    description: form.description.trim(),
    achievements: form.achievements.trim(),
    startDate: form.startDate || null,
    endDate: form.endDate || null,
  }
}

/** 去空白、去重（大小写不敏感，保留首次出现的写法） */
function normalizeList(values: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const value of values) {
    const text = value.trim()
    if (!text) {
      continue
    }
    const key = text.toLowerCase()
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    result.push(text)
  }
  return result
}
