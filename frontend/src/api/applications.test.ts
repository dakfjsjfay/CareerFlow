import { describe, expect, it } from 'vitest'

import { STATUS_LABELS, STATUS_TRANSITIONS, type ApplicationStatus } from './applications'

const ALL_STATUSES: ApplicationStatus[] = [
  'PENDING',
  'APPLIED',
  'READ',
  'INTERVIEW_INVITED',
  'INTERVIEWING',
  'OFFERED',
  'REJECTED',
  'WITHDRAWN',
  'FAILED',
  'NO_RESPONSE',
]

describe('applications api contract', () => {
  it('every status has a label and transition entry', () => {
    ALL_STATUSES.forEach((status) => {
      expect(STATUS_LABELS[status]).toBeTruthy()
      expect(STATUS_TRANSITIONS[status]).toBeDefined()
    })
  })

  it('mirrors the backend state machine', () => {
    expect(STATUS_TRANSITIONS.APPLIED).toContain('READ')
    expect(STATUS_TRANSITIONS.READ).toContain('INTERVIEW_INVITED')
    expect(STATUS_TRANSITIONS.INTERVIEW_INVITED).toContain('INTERVIEWING')
    expect(STATUS_TRANSITIONS.INTERVIEWING).toEqual(['OFFERED', 'REJECTED'])
    expect(STATUS_TRANSITIONS.NO_RESPONSE).toEqual(['READ', 'INTERVIEW_INVITED', 'REJECTED'])
  })

  it('terminal statuses cannot transition further', () => {
    expect(STATUS_TRANSITIONS.OFFERED).toHaveLength(0)
    expect(STATUS_TRANSITIONS.REJECTED).toHaveLength(0)
    expect(STATUS_TRANSITIONS.WITHDRAWN).toHaveLength(0)
    expect(STATUS_TRANSITIONS.FAILED).toHaveLength(0)
  })
})
