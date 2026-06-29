let timer

/** 轻量全局提示。无需组件，直接挂到 body。 */
export function toast(msg, ms = 2200) {
  let el = document.getElementById('app-toast')
  if (!el) {
    el = document.createElement('div')
    el.id = 'app-toast'
    el.className = 'toast'
    document.body.appendChild(el)
  }
  el.textContent = msg
  // 强制重绘以重启动画
  void el.offsetWidth
  el.classList.add('show')
  clearTimeout(timer)
  timer = setTimeout(() => el.classList.remove('show'), ms)
}
