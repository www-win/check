const BASE = '/api'

export function getToken() {
  return localStorage.getItem('token')
}
export function setAuth(d) {
  localStorage.setItem('token', d.token)
  localStorage.setItem('userId', d.userId)
  localStorage.setItem('nickname', d.nickname || '')
}
export function clearAuth() {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('nickname')
}

export class BizError extends Error {
  constructor(code, msg) {
    super(msg || '请求失败')
    this.code = code
  }
}

/**
 * 统一请求封装。
 * opts: { method, body, raw }
 *  - raw=true 时 body 原样发送（FormData），否则按 JSON 处理
 * 返回 data 字段；code!==0 抛 BizError；40100 清登录态并跳登录。
 */
export async function request(path, { method = 'GET', body, raw = false } = {}) {
  const headers = {}
  const token = getToken()
  if (token) headers['Authorization'] = 'Bearer ' + token

  const opts = { method, headers }
  if (body !== undefined && body !== null) {
    if (raw) {
      opts.body = body
    } else {
      headers['Content-Type'] = 'application/json'
      opts.body = JSON.stringify(body)
    }
  }

  let resp
  try {
    resp = await fetch(BASE + path, opts)
  } catch (e) {
    throw new BizError(-1, '无法连接服务器')
  }

  let data
  try {
    data = await resp.json()
  } catch (e) {
    throw new BizError(-1, '服务器返回异常')
  }

  if (data.code === 40100) {
    clearAuth()
    if (location.hash !== '#/login') location.hash = '#/login'
    throw new BizError(40100, data.msg)
  }
  if (data.code !== 0) {
    throw new BizError(data.code, data.msg)
  }
  return data.data
}

// ===== 学习目标 =====
export function getGoal() {
  return request('/goal')
}
export function saveGoal(body) {
  return request('/goal', { method: 'PUT', body })
}
export function clearGoal() {
  return request('/goal', { method: 'DELETE' })
}
