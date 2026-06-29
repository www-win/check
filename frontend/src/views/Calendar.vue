<script setup>
import { ref, onMounted, computed } from 'vue'
import { request } from '../api'
import { toast } from '../toast'

const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const moodEmoji = { 1: '😣', 2: '😕', 3: '😐', 4: '🙂', 5: '😄' }

function nowYM() {
  const n = new Date()
  return { y: n.getFullYear(), m: n.getMonth() + 1 }
}
const cur = ref(nowYM())
const data = ref(null)

function pad(n) {
  return String(n).padStart(2, '0')
}
function monthStr() {
  return cur.value.y + '-' + pad(cur.value.m)
}
const todayStr = (() => {
  const n = new Date()
  return n.getFullYear() + '-' + pad(n.getMonth() + 1) + '-' + pad(n.getDate())
})()

async function load() {
  try {
    data.value = await request('/checkin/calendar?month=' + monthStr())
  } catch (e) {
    toast(e.message)
  }
}
onMounted(load)

// 周一为起始，前面补空格
const cells = computed(() => {
  if (!data.value) return []
  const first = new Date(cur.value.y, cur.value.m - 1, 1)
  const lead = (first.getDay() + 6) % 7
  const arr = []
  for (let i = 0; i < lead; i++) arr.push(null)
  data.value.days.forEach((d) => arr.push(d))
  return arr
})

function dayNum(d) {
  return Number(d.date.slice(-2))
}

function prev() {
  let { y, m } = cur.value
  m--
  if (m < 1) { m = 12; y-- }
  cur.value = { y, m }
  load()
}
function next() {
  let { y, m } = cur.value
  m++
  if (m > 12) { m = 1; y++ }
  cur.value = { y, m }
  load()
}

async function onDay(d) {
  if (!d || d.status > 0) return
  if (d.date >= todayStr) {
    if (d.date === todayStr) toast('今天请到「今日」页打卡')
    return
  }
  if (!confirm('补卡 ' + d.date + ' ？将消耗 50 积分')) return
  try {
    await request('/checkin/makeup', { method: 'POST', body: { date: d.date } })
    toast('补卡成功')
    load()
  } catch (e) {
    toast(e.message)
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">签到日历</h2>
      <p class="page-sub">绿色已签到，橙色为补卡</p>
    </div>

    <div class="page" style="padding-top: 8px">
      <div class="card">
        <div class="cal-head">
          <button class="cal-nav" @click="prev">‹</button>
          <div class="cal-month">{{ cur.y }} 年 {{ cur.m }} 月</div>
          <button class="cal-nav" @click="next">›</button>
        </div>

        <div class="cal-grid">
          <div v-for="w in weekdays" :key="w" class="cal-wk">{{ w }}</div>
          <div
            v-for="(d, i) in cells"
            :key="i"
            :class="[
              'cal-cell',
              !d ? 'empty' : '',
              d && d.status === 1 ? 'checked' : '',
              d && d.status === 2 ? 'makeup' : '',
              d && d.date === todayStr ? 'today' : '',
              d && d.status === 0 && d.date < todayStr ? 'past' : ''
            ]"
            @click="onDay(d)"
          >
            <template v-if="d">
              <span>{{ dayNum(d) }}</span>
              <span v-if="d.mood" class="cell-mood">{{ moodEmoji[d.mood] }}</span>
            </template>
          </div>
        </div>

        <div class="cal-legend">
          <span><i class="dot g"></i>已签到</span>
          <span><i class="dot o"></i>补卡</span>
          <span>点击过去未签的日子可补卡</span>
        </div>
      </div>
    </div>
  </div>
</template>
