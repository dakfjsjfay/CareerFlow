/**
 * 用浏览器打印导出 PDF：预览区与打印样式一致，用户选择「另存为 PDF」。
 * 文档标题会作为默认文件名，打印结束后恢复。
 */
export function printResume(title: string): void {
  if (typeof window === 'undefined') {
    return
  }
  const previous = document.title
  document.title = title

  let restored = false
  const restore = () => {
    if (restored) {
      return
    }
    restored = true
    document.title = previous
    window.removeEventListener('afterprint', restore)
  }

  window.addEventListener('afterprint', restore)
  window.print()
  window.setTimeout(restore, 1000)
}
