<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  getCouple, bindCouple, acceptCouple, rejectCouple,
  cancelCouple, unbindCouple,
  getPartnerStatus, getPartnerCalendar, getCoupleSummary, pokePartner, getPokes,
  toast
} from '../../utils/request'

const data = ref(null)        // CoupleStatusResp
const inputCode = ref('')
const submitting = ref(false)

const moodEmoji = { 1: '😣', 2: '😕', 3: '😐', 4: '🙂', 5: '😄' }
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
function pad(n) { return String(n).padStart(2, '0') }
const now = new Date()

// 互动时间固定格式 MM-DD HH:mm（用聊天页同款解析,replace - 为 / 兼容 iOS）
function fmtPokeTime(s) {
  if (!s) return ''
  const d = new Date(String(s).replace(/-/g, '/'))
  return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}

const partner = ref(null)        // 对方 CheckinStatusResp
const summary = ref(null)        // CoupleSummaryResp
const cal = ref(null)            // 对方日历 CalendarResp
const calCur = ref({ y: now.getFullYear(), m: now.getMonth() + 1 })
const pokes = ref([])            // 互动列表
const showPoke = ref(false)
const pokeMsg = ref('')

const calCells = computed(() => {
  if (!cal.value) return []
  const first = new Date(calCur.value.y, calCur.value.m - 1, 1)
  const lead = (first.getDay() + 6) % 7
  const arr = []
  for (let i = 0; i < lead; i++) arr.push(null)
  cal.value.days.forEach((d) => arr.push(d))
  return arr
})
function dayNum(d) { return Number(d.date.slice(-2)) }
function calMonthStr() { return calCur.value.y + '-' + pad(calCur.value.m) }

function loadActive() {
  getPartnerStatus().then((d) => (partner.value = d)).catch((e) => toast(e.message))
  getCoupleSummary().then((d) => (summary.value = d)).catch((e) => toast(e.message))
  loadCalendar()
  getPokes().then((d) => (pokes.value = d)).catch(() => {})
}
function loadCalendar() {
  getPartnerCalendar(calMonthStr()).then((d) => (cal.value = d)).catch((e) => toast(e.message))
}
function calPrev() {
  let { y, m } = calCur.value
  m--; if (m < 1) { m = 12; y-- }
  calCur.value = { y, m }; loadCalendar()
}
function calNext() {
  let { y, m } = calCur.value
  m++; if (m > 12) { m = 1; y++ }
  calCur.value = { y, m }; loadCalendar()
}

function quickPoke() {
  pokePartner(null).then(() => { toast('已戳 TA 一下 👉'); getPokes().then((d) => (pokes.value = d)) })
    .catch((e) => toast(e.message))
}
function openPoke() { pokeMsg.value = ''; showPoke.value = true }
function sendPoke() {
  const msg = pokeMsg.value.trim()
  if (!msg) return toast('写点鼓励的话吧')
  pokePartner(msg).then(() => {
    toast('已送达 💌'); showPoke.value = false
    getPokes().then((d) => (pokes.value = d))
  }).catch((e) => toast(e.message))
}

onShow(() => load())

function load() {
  getCouple().then((d) => {
    data.value = d
    if (d.status === 'ACTIVE') loadActive()
  }).catch((e) => toast(e.message))
}

function copyCode() {
  if (!data.value || !data.value.myInviteCode) return
  uni.setClipboardData({ data: data.value.myInviteCode, success: () => toast('邀请码已复制') })
}

function doBind() {
  const code = inputCode.value.trim().toUpperCase()
  if (!code) return toast('请输入对方邀请码')
  submitting.value = true
  bindCouple(code)
    .then(() => { toast('已发送，等待对方同意'); inputCode.value = ''; load() })
    .catch((e) => toast(e.message))
    .finally(() => (submitting.value = false))
}

function doAccept() {
  acceptCouple().then(() => { toast('已结成情侣 💑'); load() }).catch((e) => toast(e.message))
}
function doReject() {
  rejectCouple().then(() => { toast('已拒绝'); load() }).catch((e) => toast(e.message))
}
function doCancel() {
  cancelCouple().then(() => { toast('已取消'); load() }).catch((e) => toast(e.message))
}
function doUnbind() {
  uni.showModal({
    title: '解除关系',
    content: '确定解除情侣关系？解除后将无法再查看对方打卡。',
    success: (r) => {
      if (!r.confirm) return
      unbindCouple().then(() => { toast('已解除'); load() }).catch((e) => toast(e.message))
    }
  })
}

defineExpose({ load }) // 供 Task 6 ACTIVE 内容刷新复用
</script>

<template>
  <view class="page-body">
    <block v-if="data">
      <!-- 未绑定 -->
      <block v-if="data.status === 'NONE'">
        <view class="card center">
          <view class="big">💑</view>
          <view class="title">和 TA 一起打卡</view>
          <view class="sub">把你的邀请码发给对方，或输入对方的邀请码</view>
          <view class="mycode" @tap="copyCode">
            <text class="code">{{ data.myInviteCode }}</text>
            <text class="copy">复制</text>
          </view>
        </view>
        <view class="card">
          <view class="field-label">输入对方邀请码</view>
          <input class="input" v-model="inputCode" placeholder="如 ABC234" maxlength="6" />
          <button class="btn" hover-class="btn-hover" :disabled="submitting" @tap="doBind">
            {{ submitting ? '发送中…' : '发起绑定' }}
          </button>
        </view>
      </block>

      <!-- 等待对方同意 -->
      <block v-else-if="data.status === 'PENDING_OUT'">
        <view class="card center">
          <view class="big">⏳</view>
          <view class="title">等待 {{ data.partner ? data.partner.nickname : 'TA' }} 同意</view>
          <view class="sub">对方同意后即可成为情侣</view>
          <button class="btn btn-ghost" @tap="doCancel">取消请求</button>
        </view>
      </block>

      <!-- 收到请求 -->
      <block v-else-if="data.status === 'PENDING_IN'">
        <view class="card center">
          <view class="big">💌</view>
          <view class="title">{{ data.partner ? data.partner.nickname : 'TA' }} 想和你组成情侣</view>
          <view class="sub">同意后你们可以互相查看打卡</view>
          <view class="row-actions">
            <button class="btn btn-ghost flex1" @tap="doReject">拒绝</button>
            <button class="btn flex1" hover-class="btn-hover" @tap="doAccept">同意</button>
          </view>
        </view>
      </block>

      <!-- 已建立 -->
      <block v-else-if="data.status === 'ACTIVE'">
        <!-- 头部 -->
        <view class="card center hd">
          <view class="big">💞</view>
          <view class="title">你和 {{ data.partner ? data.partner.nickname : 'TA' }}</view>
          <view v-if="data.unreadPokeCount > 0" class="badge">TA 戳了你 {{ data.unreadPokeCount }} 次</view>
        </view>

        <!-- 对方今日打卡 -->
        <view class="card">
          <view class="card-title">TA 的今日打卡</view>
          <block v-if="partner">
            <view v-if="partner.todayChecked" class="today-ok">✅ 今天已打卡</view>
            <view v-else class="today-no">⌛ 今天还没打卡，戳戳 TA 吧</view>
            <view class="mini-stats">
              <view><text class="n">{{ partner.currentStreak }}</text><text class="l">连续</text></view>
              <view><text class="n">{{ partner.totalDays }}</text><text class="l">累计</text></view>
              <view><text class="n">{{ partner.points }}</text><text class="l">积分</text></view>
            </view>
          </block>
        </view>

        <!-- 共同统计 -->
        <view class="card" v-if="summary">
          <view class="card-title">我们的共同记录</view>
          <view class="mini-stats">
            <view><text class="n">{{ summary.commonDays }}</text><text class="l">共同打卡</text></view>
            <view><text class="n">{{ summary.myStreak }}/{{ summary.partnerStreak }}</text><text class="l">各自连续</text></view>
            <view><text class="n">{{ summary.totalPoints }}</text><text class="l">合计积分</text></view>
          </view>
        </view>

        <!-- 对方日历 -->
        <view class="card" v-if="cal">
          <view class="cal-head">
            <view class="nav" @tap="calPrev">‹</view>
            <view class="month">TA 的 {{ calCur.y }}年{{ calCur.m }}月</view>
            <view class="nav" @tap="calNext">›</view>
          </view>
          <view class="grid">
            <view v-for="w in weekdays" :key="w" class="wk">{{ w }}</view>
            <view
              v-for="(d, i) in calCells"
              :key="i"
              :class="['cell', d && d.status === 1 ? 'checked' : '', d && d.status === 2 ? 'makeup' : '']"
            >
              <block v-if="d">
                <text>{{ dayNum(d) }}</text>
                <text v-if="d.mood" class="cell-mood">{{ moodEmoji[d.mood] }}</text>
              </block>
            </view>
          </view>
        </view>

        <!-- 互动 -->
        <view class="row-actions">
          <button class="btn btn-ghost flex1" @tap="quickPoke">👉 戳一下</button>
          <button class="btn flex1" hover-class="btn-hover" @tap="openPoke">💌 留言督促</button>
        </view>

        <view class="card" v-if="pokes.length">
          <view class="card-title">最近互动</view>
          <view v-for="p in pokes" :key="p.id" class="poke-item">
            <text class="poke-who">{{ p.fromMe ? '我' : (data.partner ? data.partner.nickname : 'TA') }}</text>
            <text class="poke-msg">{{ p.message || '戳了一下 👉' }}</text>
            <text class="poke-time">{{ fmtPokeTime(p.createdAt) }}</text>
          </view>
        </view>

        <view class="link-danger" @tap="doUnbind">解除关系</view>
      </block>
    </block>

    <!-- 留言弹层 -->
    <view v-if="showPoke" class="mask" @tap="showPoke = false">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">给 TA 留言</view>
        <textarea class="note" v-model="pokeMsg" maxlength="200" placeholder="今天也要加油打卡哦～" />
        <view class="sheet-actions">
          <button class="btn btn-ghost flex1" @tap="showPoke = false">取消</button>
          <button class="btn flex1" hover-class="btn-hover" @tap="sendPoke">发送</button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.card { background: #fff; border-radius: 32rpx; padding: 36rpx; margin-bottom: 24rpx; box-shadow: 0 4rpx 16rpx rgba(45,140,85,.06); }
.center { text-align: center; }
.big { font-size: 96rpx; }
.title { font-size: 36rpx; font-weight: 700; margin-top: 12rpx; }
.sub { font-size: 26rpx; color: var(--c-muted); margin-top: 10rpx; }
.mycode { display: inline-flex; align-items: center; gap: 18rpx; margin-top: 28rpx; background: #EAF6EF; border-radius: 24rpx; padding: 20rpx 32rpx; }
.code { font-size: 44rpx; font-weight: 800; letter-spacing: 6rpx; color: var(--c-primary-d); }
.copy { font-size: 24rpx; color: var(--c-primary-d); background: #fff; padding: 6rpx 20rpx; border-radius: 30rpx; }
.field-label { font-size: 26rpx; color: var(--c-muted); margin-bottom: 14rpx; }
.input { height: 92rpx; border: 2rpx solid var(--c-line); border-radius: 24rpx; padding: 0 28rpx; font-size: 36rpx; letter-spacing: 4rpx; text-align: center; margin-bottom: 24rpx; }
.row-actions { display: flex; gap: 20rpx; margin-top: 28rpx; }
.flex1 { flex: 1; }
.link-danger { text-align: center; color: #E06A5B; font-size: 26rpx; margin-top: 26rpx; }
.hd { position: relative; }
.badge { display: inline-block; margin-top: 14rpx; background: #FFE9E6; color: #E06A5B; font-size: 24rpx; padding: 8rpx 22rpx; border-radius: 30rpx; }
.card-title { font-size: 28rpx; font-weight: 700; margin-bottom: 20rpx; }
.today-ok { font-size: 30rpx; color: var(--c-primary-d); font-weight: 600; }
.today-no { font-size: 30rpx; color: var(--c-muted); }
.mini-stats { display: flex; margin-top: 20rpx; }
.mini-stats > view { flex: 1; text-align: center; display: flex; flex-direction: column; }
.mini-stats .n { font-size: 40rpx; font-weight: 800; color: var(--c-primary-d); }
.mini-stats .l { font-size: 24rpx; color: var(--c-muted); margin-top: 6rpx; }
.cal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.month { font-size: 30rpx; font-weight: 700; }
.nav { width: 64rpx; height: 64rpx; line-height: 60rpx; text-align: center; border-radius: 18rpx; background: #EAF6EF; color: var(--c-primary-d); font-size: 38rpx; }
.grid { display: flex; flex-wrap: wrap; }
.wk { width: 14.28%; text-align: center; font-size: 24rpx; color: var(--c-muted); padding-bottom: 12rpx; }
.cell { width: 14.28%; box-sizing: border-box; height: 84rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 28rpx; }
.cell text { line-height: 1.1; }
.cell .cell-mood { font-size: 20rpx; }
.cell.checked { background: var(--c-primary); color: #fff; border-radius: 18rpx; font-weight: 600; }
.cell.checked text { color: #fff; }
.cell.makeup { background: var(--c-accent); color: #fff; border-radius: 18rpx; font-weight: 600; }
.cell.makeup text { color: #fff; }
.poke-item { display: flex; gap: 16rpx; padding: 16rpx 0; border-bottom: 2rpx solid var(--c-line); font-size: 28rpx; }
.poke-item:last-child { border-bottom: none; }
.poke-who { color: var(--c-primary-d); font-weight: 700; flex-shrink: 0; }
.poke-msg { color: var(--c-text); flex: 1; min-width: 0; }
.poke-time { flex-shrink: 0; color: var(--c-muted); font-size: 22rpx; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(20,32,26,.45); display: flex; align-items: flex-end; z-index: 100; }
.sheet { width: 100%; background: #fff; border-radius: 36rpx 36rpx 0 0; padding: 36rpx; }
.sheet-title { font-size: 36rpx; font-weight: 700; text-align: center; margin-bottom: 24rpx; }
.note { width: 100%; min-height: 140rpx; box-sizing: border-box; padding: 20rpx 24rpx; border: 2rpx solid var(--c-line); border-radius: 24rpx; font-size: 28rpx; }
.sheet-actions { display: flex; gap: 20rpx; margin-top: 28rpx; }
</style>
