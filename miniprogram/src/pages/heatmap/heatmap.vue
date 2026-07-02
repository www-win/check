<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getHeatmap, toast } from '../../utils/request'

const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const now = new Date()
const year = ref(now.getFullYear())
const data = ref(null)

function pad(n) { return String(n).padStart(2, '0') }
function ymd(y, m, d) { return y + '-' + pad(m) + '-' + pad(d) }
const todayStr = ymd(now.getFullYear(), now.getMonth() + 1, now.getDate())

onShow(() => load())

function load() {
  getHeatmap(year.value)
    .then((d) => (data.value = d))
    .catch((e) => toast(e.message))
}

function prevYear() { if (year.value > 2000) { year.value--; load() } }
function nextYear() { if (year.value < now.getFullYear()) { year.value++; load() } }

const months = computed(() => {
  const signed = new Set(data.value ? data.value.signed : [])
  const makeup = new Set(data.value ? data.value.makeup : [])
  const y = year.value
  const list = []
  for (let m = 1; m <= 12; m++) {
    const first = new Date(y, m - 1, 1)
    const lead = (first.getDay() + 6) % 7
    const daysInMonth = new Date(y, m, 0).getDate()
    const cells = []
    for (let i = 0; i < lead; i++) cells.push(null)
    let checked = 0
    for (let d = 1; d <= daysInMonth; d++) {
      const key = ymd(y, m, d)
      let status = 0
      if (makeup.has(key)) { status = 2; checked++ }
      else if (signed.has(key)) { status = 1; checked++ }
      cells.push({ day: d, status, isToday: key === todayStr })
    }
    list.push({ month: m, cells, checked })
  }
  return list
})

const summary = computed(() => (data.value ? data.value.summary : { totalDays: 0, longestStreak: 0, makeupDays: 0, rate: 0 }))
</script>

<template>
  <view class="page-body">
    <view class="card">
      <view class="head">
        <view :class="['nav', year <= 2000 ? 'disabled' : '']" @tap="prevYear">‹</view>
        <view class="yr">{{ year }} 年</view>
        <view :class="['nav', year >= now.getFullYear() ? 'disabled' : '']" @tap="nextYear">›</view>
      </view>
      <view class="stats">
        <view class="stat"><text class="n">{{ summary.totalDays }}</text><text class="l">签到</text></view>
        <view class="stat"><text class="n">{{ summary.longestStreak }}</text><text class="l">最长连续</text></view>
        <view class="stat"><text class="n">{{ summary.makeupDays }}</text><text class="l">补卡</text></view>
        <view class="stat"><text class="n">{{ summary.rate }}%</text><text class="l">打卡率</text></view>
      </view>
      <view class="legend">
        <view><text class="dot g"></text>已签到</view>
        <view><text class="dot o"></text>补卡</view>
        <view><text class="dot e"></text>未签</view>
      </view>
    </view>

    <view class="card month-card" v-for="mo in months" :key="mo.month">
      <view class="m-head">
        <text class="m-title">{{ mo.month }} 月</text>
        <text class="m-count">打卡 {{ mo.checked }} 天</text>
      </view>
      <view class="grid">
        <view v-for="w in weekdays" :key="w" class="wk">{{ w }}</view>
        <view
          v-for="(c, i) in mo.cells"
          :key="i"
          :class="['cell', c && c.status === 1 ? 'checked' : '', c && c.status === 2 ? 'makeup' : '', c && c.isToday ? 'today' : '']"
        >
          <text v-if="c">{{ c.day }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.yr { font-size: 34rpx; font-weight: 700; }
.nav { width: 64rpx; height: 64rpx; line-height: 60rpx; text-align: center; border-radius: 18rpx; background: #EAF6EF; color: var(--c-primary-d); font-size: 38rpx; }
.nav.disabled { opacity: .35; }
.stats { display: flex; justify-content: space-between; margin-bottom: 20rpx; }
.stat { flex: 1; display: flex; flex-direction: column; align-items: center; }
.stat .n { font-size: 40rpx; font-weight: 700; color: var(--c-primary-d); }
.stat .l { font-size: 22rpx; color: var(--c-muted); margin-top: 4rpx; }
.legend { display: flex; align-items: center; gap: 28rpx; font-size: 24rpx; color: var(--c-muted); }
.legend view { display: flex; align-items: center; }
.dot { width: 20rpx; height: 20rpx; border-radius: 6rpx; margin-right: 8rpx; }
.dot.g { background: var(--c-primary); }
.dot.o { background: var(--c-accent); }
.dot.e { background: #EBEDF0; }
.m-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 16rpx; }
.m-title { font-size: 32rpx; font-weight: 700; }
.m-count { font-size: 24rpx; color: var(--c-muted); }
.grid { display: flex; flex-wrap: wrap; }
.wk { width: 14.28%; text-align: center; font-size: 22rpx; color: var(--c-muted); padding-bottom: 10rpx; }
.cell { width: 14.28%; box-sizing: border-box; height: 68rpx; display: flex; align-items: center; justify-content: center; font-size: 26rpx; color: var(--c-text); }
.cell.checked { background: var(--c-primary); color: #fff; border-radius: 14rpx; font-weight: 600; }
.cell.makeup { background: var(--c-accent); color: #fff; border-radius: 14rpx; font-weight: 600; }
.cell.today { outline: 3rpx solid var(--c-primary-d); border-radius: 14rpx; }
</style>
