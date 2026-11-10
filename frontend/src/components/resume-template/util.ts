/** resume-design 的日期格式：2025-03-01 → 2025.03；结束时间支持「至今」。 */
export function formatDate(
  value: string | [string | null, string | null] | null | undefined,
): string {
  if (Array.isArray(value)) {
    const start = formatMonth(value[0])
    const endRaw = value[1]
    const end =
      endRaw && !/^\d{4}([-/.]\d{1,2})?([-/.]\d{1,2})?$/.test(endRaw)
        ? endRaw
        : formatMonth(endRaw)
    if (!start && !end) {
      return ''
    }
    return `${start || '未知'}-${end || '至今'}`
  }
  return formatMonth(value)
}

function formatMonth(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const match = String(value).match(/^(\d{4})(?:[-/.](\d{1,2}))?/)
  if (!match) {
    return String(value)
  }
  return match[2] ? `${match[1]}.${match[2].padStart(2, '0')}` : match[1]
}

export function escapeHtml(value: string | null | undefined): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function toParagraphs(
  values: Array<string | null | undefined>,
): Array<{ content: { value: string } }> {
  const lines = values
    .flatMap((value) => (value == null ? [] : String(value).split(/\r?\n/)))
    .map((line) => line.trim())
    .filter((line) => line.length > 0)

  return lines.map((line) => ({
    content: { value: `<p>${escapeHtml(line)}</p>` },
  }))
}

export function textToNumber(value: string): number {
  switch (value) {
    case '了解':
      return 25
    case '一般':
      return 50
    case '熟悉':
      return 75
    case '精通':
      return 100
    default:
      return 0
  }
}

export function numberToText(value: number): string {
  if (value <= 25) {
    return '一般'
  }
  if (value <= 50) {
    return '掌握'
  }
  if (value <= 75) {
    return '熟练'
  }
  return '精通'
}
