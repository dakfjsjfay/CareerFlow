import { afterEach, describe, expect, it, vi } from 'vitest'

import { printResume } from './exportPdf'

function stubPrint() {
  const print = vi.fn()
  Object.defineProperty(window, 'print', { configurable: true, value: print })
  return print
}

describe('printResume', () => {
  afterEach(() => {
    vi.useRealTimers()
    document.title = ''
  })

  it('sets the document title before printing', () => {
    const print = stubPrint()
    document.title = 'CareerFlow'

    printResume('张同学-v3')

    expect(print).toHaveBeenCalledTimes(1)
    expect(document.title).toBe('张同学-v3')
  })

  it('restores the title after the afterprint event', () => {
    stubPrint()
    document.title = 'CareerFlow'

    printResume('张同学-v3')
    window.dispatchEvent(new Event('afterprint'))

    expect(document.title).toBe('CareerFlow')
  })

  it('restores the title with a timeout fallback', () => {
    vi.useFakeTimers()
    stubPrint()
    document.title = 'CareerFlow'

    printResume('张同学-v3')
    vi.advanceTimersByTime(1000)

    expect(document.title).toBe('CareerFlow')
  })
})
