import { describe, expect, it } from 'vitest'

import { formatDate } from './util'

describe('formatDate', () => {
  it('formats full ISO dates to year.month', () => {
    expect(formatDate('2025-06-30')).toBe('2025.06')
    expect(formatDate(['2025-03-01', '2025-06-30'])).toBe('2025.03-2025.06')
  })

  it('keeps the "至今" label for open ranges', () => {
    expect(formatDate(['2024-09-01', '至今'])).toBe('2024.09-至今')
    expect(formatDate(['2024-09-01', null])).toBe('2024.09-至今')
  })

  it('handles year-only and empty values', () => {
    expect(formatDate('2028')).toBe('2028')
    expect(formatDate(null)).toBe('')
  })
})
