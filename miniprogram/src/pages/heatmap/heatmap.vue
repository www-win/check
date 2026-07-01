<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getHeatmap, toast } from '../../utils/request'

const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const now = new Date()
const year = ref(now.getFullYear())
const data = ref(null)

onShow(() => load())

function load() {
  getHeatmap(year.value)
    .then((d) => (data.value = d))
    .catch((e) => toast(e.message))
}

function prevYear() { year.value--; load() }
function nextYear() { if (year.value < now.getFullYear()) { year.value++; load() } }

// 周一为每列起点，行 = (getDay()+6)%7
function pad(n) { return String(n).padStart(2, '0') }
function ymd(dt) { return dt.getFullYear() + '-' + pad(dt.getMonth() + 1) + '-' + pad(dt.getDate()) }

// 生成整年网格：按列（周）× 7 行，每格 { date, status, month, firstOfMonth }
const grid = computed(() => {
  const signed = new Set(data.value ? data.value.signed : [])
  const makeup = new Set(data.value ? data.value.makeup : [])
  const y = year.value
  const jan1 = new Date(y, 0, 1)
  const dec31 = new Date(y, 11, 31)
  // 起点回退到 jan1 所在周的周一
  const start = new Date(jan1)
  const lead = (jan1.getDay() + 6) % 7
  start.setDate(start.getDate() - lead)

  const columns = []
  let col = []
  const cursor = new Date(start)
  while (cursor <= dec31 || col.length > 0) {
    const inYear = cursor.getFullYear() === y
    const key = ymd(cursor)
    let status = -1 // -1=非本年占位
    if (inYear) {
      status = makeup.has(key) ? 2 : (signed.has(key) ? 1 : 0)
    }
    col.push({
      key,
      status,
      day: cursor.getDate(),
      month: cursor.getMonth() + 1,
      firstOfMonth: inYear && cursor.getDate() === 1
    })
    if (col.length === 7) { columns.push(col); col = [] }
    cursor.setDate(cursor.getDate() + 1)
    if (cursor > dec31 && col.length === 0) break
  }
  if (col.length > 0) {
    while (col.length < 7) col.push({ key: 'pad-' + col.length, status: -1, firstOfMonth: false })
    columns.push(col)
  }
  return columns
})

// 每列顶部的月份标签（该列是否含某月 1 号）
function colMonth(column) {
  const f = column.find((c) => c.firstOfMonth)
  return f ? f.month : ''
}

const summary = computed(() => (data.value ? data.value.summary : { totalDays: 0, longestStreak: 0, makeupDays: 0, rate: 0 }))
</script>

<template>
  <view class="page-body">
    <view class="card">
      <view class="head">
        <view class="nav" @tap="prevYear">‹</view>
        <view class="yr">{{ year }} 年</view>
        <view :class="['nav', year >= now.getFullYear() ? 'disabled' : '']" @tap="nextYear">›</view>
      </view>

      <view class="stats">
        <view class="stat"><text class="n">{{ summary.totalDays }}</text><text class="l">签到</text></view>
        <view class="stat"><text class="n">{{ summary.longestStreak }}</text><text class="l">最长连续</text></view>
        <view class="stat"><text class="n">{{ summary.makeupDays }}</text><text class="l">补卡</text></view>
        <view class="stat"><text class="n">{{ summary.rate }}%</text><text class="l">打卡率</text></view>
      </view>

      <scroll-view scroll-x class="hm-scroll">
        <view class="hm">
          <view class="months">
            <view v-for="(column, ci) in grid" :key="'m' + ci" class="mcol">{{ colMonth(column) }}</view>
          </view>
          <view class="cols">
            <view v-for="(column, ci) in grid" :key="ci" class="col">
              <view
                v-for="(cell, ri) in column"
                :key="ri"
                :class="['cell', cell.status === 0 ? 's0' : '', cell.status === 1 ? 's1' : '', cell.status === 2 ? 's2' : '', cell.status === -1 ? 'pad' : '']"
              ></view>
            </view>
          </view>
        </view>
      </scroll-view>

      <view class="legend">
        <view><text class="dot g"></text>已签到</view>
        <view><text class="dot o"></text>补卡</view>
        <view><text class="dot e"></text>未签</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.yr { font-size: 34rpx; font-weight: 700; }
.nav { width: 64rpx; height: 64rpx; line-height: 60rpx; text-align: center; border-radius: 18rpx; background: #EAF6EF; color: var(--c-primary-d); font-size: 38rpx; }
.nav.disabled { opacity: .35; }
.stats { display: flex; justify-content: space-between; margin-bottom: 28rpx; }
.stat { flex: 1; display: flex; flex-direction: column; align-items: center; }
.stat .n { font-size: 40rpx; font-weight: 700; color: var(--c-primary-d); }
.stat .l { font-size: 22rpx; color: var(--c-muted); margin-top: 4rpx; }
.hm-scroll { width: 100%; }
.months { display: flex; margin-bottom: 6rpx; }
.mcol { width: 26rpx; font-size: 18rpx; color: var(--c-muted); text-align: left; }
.cols { display: flex; }
.col { display: flex; flex-direction: column; }
.cell { width: 20rpx; height: 20rpx; margin: 3rpx; border-radius: 4rpx; background: #EBEDF0; }
.cell.s1 { background: var(--c-primary); }
.cell.s2 { background: var(--c-accent); }
.cell.pad { background: transparent; }
.legend { display: flex; align-items: center; gap: 28rpx; margin-top: 26rpx; font-size: 24rpx; color: var(--c-muted); }
.legend view { display: flex; align-items: center; }
.dot { width: 18rpx; height: 18rpx; border-radius: 4rpx; margin-right: 8rpx; }
.dot.g { background: var(--c-primary); }
.dot.o { background: var(--c-accent); }
.dot.e { background: #EBEDF0; }
</style>
