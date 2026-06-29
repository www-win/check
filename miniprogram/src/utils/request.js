import { BASE_URL } from './config'

export class BizError extends Error {
  constructor(code, msg) {
    super(msg || '请求失败')
    this.code = code
  }
}

/**
 * 统一请求封装（基于 uni.request）。
 * 自动带 token；code!==0 抛 BizError；40100 清登录态并回登录页。
 */
export function request(path, { method = 'GET', data } = {}) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    uni.request({
      url: BASE_URL + '/api' + path,
      method,
      data,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        const body = res.data
        if (!body || typeof body !== 'object') {
          reject(new BizError(-1, '服务器返回异常'))
          return
        }
        if (body.code === 40100) {
          uni.removeStorageSync('token')
          uni.reLaunch({ url: '/pages/login/login' })
          reject(new BizError(40100, body.msg))
          return
        }
        if (body.code !== 0) {
          reject(new BizError(body.code, body.msg))
          return
        }
        resolve(body.data)
      },
      fail: () => reject(new BizError(-1, '无法连接服务器'))
    })
  })
}

export function toast(title) {
  uni.showToast({ title, icon: 'none' })
}

// ===== 业务封装 =====
export const getStatus = () => request('/checkin/status')
export const getCalendar = (month) => request('/checkin/calendar?month=' + month)
export const getGoal = () => request('/goal')
export const saveGoal = (data) => request('/goal', { method: 'PUT', data })
export const clearGoal = () => request('/goal', { method: 'DELETE' })
