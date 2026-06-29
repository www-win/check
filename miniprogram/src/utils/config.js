// 后端地址。
// - 本地开发（H5 / 微信开发者工具模拟器，需勾选「不校验合法域名」）：用 localhost 或云托管公网域名
// - 微信小程序正式调用：走「微信云调用」(wx.cloud.callContainer)，免备案、免合法域名，见下方 CLOUD_*
export const BASE_URL = 'https://studybuddy-backend-276015-9-1448466912.sh.run.tcloudbase.com'

// 微信云托管「云调用」配置（微信小程序端使用，免合法域名/免备案）
export const CLOUD_ENV = 'prod-d1g0jar3te9fbb350'
export const CLOUD_SERVICE = 'studybuddy-backend'
