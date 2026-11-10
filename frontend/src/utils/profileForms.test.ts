import { describe, expect, it } from 'vitest'

import type { ProfileData, ProfileExperience, ProfileProject } from '@/api/profile'

import {
  emptyExperienceForm,
  emptyProfileForm,
  emptyProjectForm,
  experienceToForm,
  formToExperiencePayload,
  formToProfilePayload,
  formToProjectPayload,
  profileToForm,
  projectToForm,
} from './profileForms'

const profile: ProfileData = {
  id: 1,
  realName: '张三',
  phone: '13800000000',
  school: '某某大学',
  major: '计算机科学与技术',
  degree: '本科',
  graduationYear: 2027,
  skills: ['Java', 'MySQL'],
  targetPosition: 'Java 后端',
  targetCity: '杭州',
  expectedSalary: '8-12K',
  projects: [],
  experiences: [],
}

describe('profile form', () => {
  it('starts empty when no profile exists', () => {
    expect(profileToForm(null)).toEqual(emptyProfileForm())
  })

  it('copies profile fields and clones the skills list', () => {
    const form = profileToForm(profile)

    expect(form.realName).toBe('张三')
    expect(form.graduationYear).toBe(2027)
    expect(form.skills).toEqual(['Java', 'MySQL'])
    expect(form.skills).not.toBe(profile.skills)
  })

  it('trims text values and blank strings clear the field', () => {
    const form = profileToForm(profile)
    form.realName = '  李四  '
    form.phone = '   '
    form.skills = [' Java ', '', 'MySQL', ' java ']

    const payload = formToProfilePayload(form)

    expect(payload.realName).toBe('李四')
    expect(payload.phone).toBe('')
    expect(payload.skills).toEqual(['Java', 'MySQL'])
    expect(payload.graduationYear).toBe(2027)
  })
})

const project: ProfileProject = {
  id: 21,
  projectName: '学生管理系统',
  role: '后端开发',
  description: '增删改查',
  techStack: ['Java', 'MySQL'],
  startDate: '2025-03-01',
  endDate: '2025-06-30',
}

describe('project form', () => {
  it('starts empty when adding a project', () => {
    expect(projectToForm(null)).toEqual(emptyProjectForm())
  })

  it('prefills an existing project', () => {
    expect(projectToForm(project)).toEqual({
      projectName: '学生管理系统',
      role: '后端开发',
      description: '增删改查',
      techStack: ['Java', 'MySQL'],
      startDate: '2025-03-01',
      endDate: '2025-06-30',
    })
  })

  it('trims text and turns empty dates into null for LocalDate binding', () => {
    const form = projectToForm(project)
    form.projectName = '  校园二手交易平台 '
    form.role = '  '
    form.techStack = [' Java ', 'Spring Boot', 'java']
    form.endDate = ''

    expect(formToProjectPayload(form)).toEqual({
      projectName: '校园二手交易平台',
      role: '',
      description: '增删改查',
      techStack: ['Java', 'Spring Boot'],
      startDate: '2025-03-01',
      endDate: null,
    })
  })
})

const experience: ProfileExperience = {
  id: 31,
  company: '某某科技',
  position: '后端实习生',
  description: '参与接口开发',
  achievements: '完成 3 个接口',
  startDate: '2025-07-01',
  endDate: '2025-09-30',
}

describe('experience form', () => {
  it('starts empty when adding an experience', () => {
    expect(experienceToForm(null)).toEqual(emptyExperienceForm())
  })

  it('prefills an existing experience', () => {
    expect(experienceToForm(experience)).toEqual({
      company: '某某科技',
      position: '后端实习生',
      description: '参与接口开发',
      achievements: '完成 3 个接口',
      startDate: '2025-07-01',
      endDate: '2025-09-30',
    })
  })

  it('trims text and turns empty dates into null for LocalDate binding', () => {
    const form = experienceToForm(experience)
    form.company = '  某某科技 '
    form.position = ' '
    form.startDate = ''

    expect(formToExperiencePayload(form)).toEqual({
      company: '某某科技',
      position: '',
      description: '参与接口开发',
      achievements: '完成 3 个接口',
      startDate: null,
      endDate: '2025-09-30',
    })
  })
})
