import { BASE_URL, CLOUD_ENV, CLOUD_SERVICE } from './config'

export class BizError extends Error {
  constructor(code, msg) {
    super(msg || '请求失败')
    this.code = code
  }
}

// 统一处理后端响应体：code!==0 抛 BizError；40100 清登录态并回登录页。
function handleBody(body, resolve, reject) {
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
}

/**
 * 统一请求封装。自动带 token。
 * - 微信小程序：走微信云调用 wx.cloud.callContainer（免合法域名/免备案）
 * - 其他端（H5/开发）：走 uni.request + BASE_URL
 */
export function request(path, { method = 'GET', data } = {}) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    const authHeader = token ? { Authorization: 'Bearer ' + token } : {}

    // #ifdef MP-WEIXIN
    wx.cloud.callContainer({
      config: { env: CLOUD_ENV },
      path: '/api' + path,
      method,
      header: {
        'X-WX-SERVICE': CLOUD_SERVICE,
        'content-type': 'application/json',
        ...authHeader
      },
      data,
      success: (res) => handleBody(res.data, resolve, reject),
      fail: (err) => {
        console.error('[callContainer fail]', path, err)
        reject(new BizError(-1, (err && err.errMsg) || '无法连接服务器'))
      }
    })
    // #endif

    // #ifndef MP-WEIXIN
    uni.request({
      url: BASE_URL + '/api' + path,
      method,
      data,
      header: authHeader,
      success: (res) => handleBody(res.data, resolve, reject),
      fail: () => reject(new BizError(-1, '无法连接服务器'))
    })
    // #endif
  })
}

export function toast(title) {
  uni.showToast({ title, icon: 'none' })
}

// ===== 业务封装 =====
export const getStatus = () => request('/checkin/status')
export const getCalendar = (month) => request('/checkin/calendar?month=' + month)
export const getHeatmap = (year) => request('/checkin/heatmap?year=' + year)
export const getAchievements = () => request('/achievements')
export const updateNickname = (nickname) => request('/user/nickname', { method: 'PUT', data: { nickname } })
export const getGoal = () => request('/goal')
export const saveGoal = (data) => request('/goal', { method: 'PUT', data })
export const clearGoal = () => request('/goal', { method: 'DELETE' })

// ===== 好友 =====
export const getFriends = () => request('/friends')
export const addFriend = (inviteCode) => request('/friends/requests', { method: 'POST', data: { inviteCode } })
export const acceptFriend = (id) => request('/friends/requests/' + id + '/accept', { method: 'POST' })
export const rejectFriend = (id) => request('/friends/requests/' + id + '/reject', { method: 'POST' })
export const cancelFriend = (id) => request('/friends/requests/' + id + '/cancel', { method: 'POST' })
export const removeFriend = (userId) => request('/friends/' + userId, { method: 'DELETE' })

// ===== 情侣 =====
export const getCouple = () => request('/couple')
export const bindCouple = (inviteCode) => request('/couple/bind', { method: 'POST', data: { inviteCode } })
export const acceptCouple = () => request('/couple/accept', { method: 'POST' })
export const rejectCouple = () => request('/couple/reject', { method: 'POST' })
export const cancelCouple = () => request('/couple/cancel', { method: 'POST' })
export const unbindCouple = () => request('/couple', { method: 'DELETE' })
export const getPartnerStatus = () => request('/couple/partner/status')
export const getPartnerCalendar = (month) => request('/couple/partner/calendar?month=' + month)
export const getCoupleSummary = () => request('/couple/summary')
export const pokePartner = (message) => request('/couple/poke', { method: 'POST', data: { message: message || null } })
export const getPokes = () => request('/couple/pokes')

// ===== 聊天 =====
export const getConversations = () => request('/chat/conversations')
export const getMessages = (peerId, afterId) =>
  request('/chat/messages?peerId=' + peerId + (afterId ? '&afterId=' + afterId : ''))
export const sendMessage = (peerId, content) => request('/chat/messages', { method: 'POST', data: { peerId, content } })
export const markChatRead = (peerId) => request('/chat/read', { method: 'POST', data: { peerId } })
