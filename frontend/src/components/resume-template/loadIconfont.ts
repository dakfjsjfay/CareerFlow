const SCRIPT_ID = 'resume-iconfont'

/** 只在第一次渲染简历时加载 580KB 的图标雪碧图。 */
export function loadIconfont(): void {
  if (typeof document === 'undefined' || document.getElementById(SCRIPT_ID)) {
    return
  }
  const script = document.createElement('script')
  script.id = SCRIPT_ID
  script.src = '/static/iconfont/iconfont.js'
  script.async = true
  document.head.appendChild(script)
}
