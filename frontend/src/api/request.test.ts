import { describe, expect, it } from 'vitest'

import { parseFilename } from './request'

describe('content-disposition filename parsing', () => {
  it('prefers RFC 5987 UTF-8 filename', () => {
    const value =
      "attachment; filename=\"resume.docx\"; filename*=UTF-8''%E5%BC%A0%E5%90%8C%E5%AD%A6-v3.docx"
    expect(parseFilename(value)).toBe('张同学-v3.docx')
  })

  it('falls back to quoted filename', () => {
    expect(parseFilename('attachment; filename="my resume.docx"')).toBe('my resume.docx')
  })

  it('uses default when header is missing', () => {
    expect(parseFilename(undefined)).toBe('resume.docx')
  })
})
