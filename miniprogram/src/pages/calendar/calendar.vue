<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request, getCalendar, toast } from '../../utils/request'

const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const moodEmoji = { 1: '😣', 2: '😕', 3: '😐', 4: '🙂', 5: '😄' }

function pad(n) { return String(n).padStart(2, '0') }
const now = new Date()
const cur = ref({ y: now.getFullYear(), m: now.getMonth() + 1 })
const data = ref(null)

const todayStr = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate())

onShow(() => load())

function monthStr() { return cur.value.y + '-' + pad(cur.value.m) }
function load() {
  getCalendar(monthStr()).then((d) => (data.value = d)).catch((e) => toast(e.message))
}

const cells = computed(() => {
  if (!data.value) return []
  const first = new Date(cur.value.y, cur.value.m - 1, 1)
  const lead = (first.getDay() + 6) % 7
  const arr = []
  for (let i = 0; i < lead; i++) arr.push(null)
  data.value.days.forEach((d) => arr.push(d))
  return arr
})

function dayNum(d) { return Number(d.date.slice(-2)) }

function prev() {
  let { y, m } = cur.value
  m--; if (m < 1) { m = 12; y-- }
  cur.value = { y, m }; load()
}
function next() {
  let { y, m } = cur.value
  m++; if (m > 12) { m = 1; y++ }
  cur.value = { y, m }; load()
}

function onDay(d) {
  if (!d || d.status > 0) return
  if (d.date >= todayStr) {
    if (d.date === todayStr) toast('今天请到「今日」页打卡')
    return
  }
  uni.showModal({
    title: '补卡',
    content: '补卡 ' + d.date + ' ？将消耗 50 积分',
    success: (r) => {
      if (!r.confirm) return
      request('/checkin/makeup', { method: 'POST', data: { date: d.date } })
        .then(() => { toast('补卡成功'); load() })
        .catch((e) => toast(e.message))
    }
  })
}
</script>

<template>
  <view class="page-body">
    <view class="card">
      <view class="cal-head">
        <view class="nav" @tap="prev">‹</view>
        <view class="month">{{ cur.y }} 年 {{ cur.m }} 月</view>
        <view class="nav" @tap="next">›</view>
      </view>

      <view class="grid">
        <view v-for="w in weekdays" :key="w" class="wk">{{ w }}</view>
        <view
          v-for="(d, i) in cells"
          :key="i"
          :class="['cell', d && d.status === 1 ? 'checked' : '', d && d.status === 2 ? 'makeup' : '', d && d.date === todayStr ? 'today' : '']"
          @tap="onDay(d)"
        >
          <block v-if="d">
            <text>{{ dayNum(d) }}</text>
            <text v-if="d.mood" class="cell-mood">{{ moodEmoji[d.mood] }}</text>
          </block>
        </view>
      </view>

      <view class="legend">
        <view><text class="dot g"></text>已签到</view>
        <view><text class="dot o"></text>补卡</view>
        <view class="hint">点过去未签的日子可补卡</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.cal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.month { font-size: 34rpx; font-weight: 700; }
.nav { width: 64rpx; height: 64rpx; line-height: 60rpx; text-align: center; border-radius: 18rpx; background: #EAF6EF; color: var(--c-primary-d); font-size: 38rpx; }
.grid { display: flex; flex-wrap: wrap; }
.wk { width: 14.28%; text-align: center; font-size: 24rpx; color: var(--c-muted); padding-bottom: 12rpx; }
.cell { width: 14.28%; box-sizing: border-box; height: 84rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 28rpx; }
.cell text { line-height: 1.1; }
.cell .cell-mood { font-size: 20rpx; }
.checked { color: #fff; }
.checked text { color: #fff; }
.cell.checked { background: var(--c-primary); border-radius: 18rpx; font-weight: 600; }
.cell.makeup { background: var(--c-accent); color: #fff; border-radius: 18rpx; font-weight: 600; }
.cell.makeup text { color: #fff; }
.cell.today { outline: 3rpx solid var(--c-primary-d); border-radius: 18rpx; }
.legend { display: flex; align-items: center; gap: 28rpx; margin-top: 26rpx; font-size: 24rpx; color: var(--c-muted); }
.legend view { display: flex; align-items: center; }
.dot { width: 18rpx; height: 18rpx; border-radius: 50%; margin-right: 8rpx; }
.dot.g { background: var(--c-primary); }
.dot.o { background: var(--c-accent); }
.hint { color: var(--c-muted); }
</style>
