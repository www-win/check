<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  getCouple, bindCouple, acceptCouple, rejectCouple, cancelCouple, unbindCouple,
  getPartnerStatus, getPartnerCalendar, getCoupleSummary, pokePartner, getPokes
} from '../api'
import { toast } from '../toast'

const router = useRouter()

const data = ref(null) // CoupleStatusResp
const inputCode = ref('')
const submitting = ref(false)

// ACTIVE 态数据
const moodEmoji = { 1: '😣', 2: '😕', 3: '😐', 4: '🙂', 5: '😄' }
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
function pad(n) { return String(n).padStart(2, '0') }
const now = new Date()

const partner = ref(null) // 对方 CheckinStatusResp
const summary = ref(null) // CoupleSummaryResp
const cal = ref(null) // 对方日历 CalendarResp
const calCur = ref({ y: now.getFullYear(), m: now.getMonth() + 1 })
const pokes = ref([])
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

onMounted(load)

async function load() {
  try {
    const d = await getCouple()
    data.value = d
    if (d.status === 'ACTIVE') loadActive()
  } catch (e) {
    toast(e.message)
  }
}

async function loadActive() {
  try { partner.value = await getPartnerStatus() } catch (e) { toast(e.message) }
  try { summary.value = await getCoupleSummary() } catch (e) { toast(e.message) }
  loadCalendar()
  try { pokes.value = await getPokes() } catch (e) { /* 忽略 */ }
}
async function loadCalendar() {
  try { cal.value = await getPartnerCalendar(calMonthStr()) } catch (e) { toast(e.message) }
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

async function copyCode() {
  if (!data.value || !data.value.myInviteCode) return
  const code = data.value.myInviteCode
  try {
    await navigator.clipboard.writeText(code)
    toast('邀请码已复制')
  } catch (e) {
    toast('邀请码：' + code)
  }
}

async function doBind() {
  const code = inputCode.value.trim().toUpperCase()
  if (!code) return toast('请输入对方邀请码')
  submitting.value = true
  try {
    await bindCouple(code)
    toast('已发送，等待对方同意')
    inputCode.value = ''
    load()
  } catch (e) {
    toast(e.message)
  } finally {
    submitting.value = false
  }
}

async function doAccept() {
  try { await acceptCouple(); toast('已结成情侣 💑'); load() } catch (e) { toast(e.message) }
}
async function doReject() {
  try { await rejectCouple(); toast('已拒绝'); load() } catch (e) { toast(e.message) }
}
async function doCancel() {
  try { await cancelCouple(); toast('已取消'); load() } catch (e) { toast(e.message) }
}
async function doUnbind() {
  if (!confirm('确定解除情侣关系？解除后将无法再查看对方打卡。')) return
  try { await unbindCouple(); toast('已解除'); load() } catch (e) { toast(e.message) }
}

async function quickPoke() {
  try {
    await pokePartner(null)
    toast('已戳 TA 一下 👉')
    pokes.value = await getPokes()
  } catch (e) { toast(e.message) }
}
function openPoke() { pokeMsg.value = ''; showPoke.value = true }
async function sendPoke() {
  const msg = pokeMsg.value.trim()
  if (!msg) return toast('写点鼓励的话吧')
  try {
    await pokePartner(msg)
    toast('已送达 💌')
    showPoke.value = false
    pokes.value = await getPokes()
  } catch (e) { toast(e.message) }
}
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">情侣空间</h2>
      <p class="page-sub">和 TA 一起坚持打卡</p>
    </div>

    <div class="page" style="padding-top: 8px" v-if="data">
      <!-- 未绑定 -->
      <template v-if="data.status === 'NONE'">
        <div class="card center">
          <div class="big">💑</div>
          <div class="ctitle">和 TA 一起打卡</div>
          <div class="csub">把你的邀请码发给对方，或输入对方的邀请码</div>
          <button class="mycode" @click="copyCode">
            <span class="code">{{ data.myInviteCode }}</span>
            <span class="copy">复制</span>
          </button>
        </div>
        <div class="card" style="margin-top: 14px">
          <div class="field-label" style="margin-top: 0">输入对方邀请码</div>
          <input class="input code-input" v-model="inputCode" placeholder="如 ABC234" maxlength="6" />
          <button class="btn" style="margin-top: 14px" :disabled="submitting" @click="doBind">
            {{ submitting ? '发送中…' : '发起绑定' }}
          </button>
        </div>
      </template>

      <!-- 等待对方同意 -->
      <template v-else-if="data.status === 'PENDING_OUT'">
        <div class="card center">
          <div class="big">⏳</div>
          <div class="ctitle">等待 {{ data.partner ? data.partner.nickname : 'TA' }} 同意</div>
          <div class="csub">对方同意后即可成为情侣</div>
          <button class="btn btn-ghost" style="margin-top: 18px" @click="doCancel">取消请求</button>
        </div>
      </template>

      <!-- 收到请求 -->
      <template v-else-if="data.status === 'PENDING_IN'">
        <div class="card center">
          <div class="big">💌</div>
          <div class="ctitle">{{ data.partner ? data.partner.nickname : 'TA' }} 想和你组成情侣</div>
          <div class="csub">同意后你们可以互相查看打卡</div>
          <div class="row-actions">
            <button class="btn btn-ghost" @click="doReject">拒绝</button>
            <button class="btn" @click="doAccept">同意</button>
          </div>
        </div>
      </template>

      <!-- 已建立 -->
      <template v-else-if="data.status === 'ACTIVE'">
        <div class="card center">
          <div class="big">💞</div>
          <div class="ctitle">你和 {{ data.partner ? data.partner.nickname : 'TA' }}</div>
          <div v-if="data.unreadPokeCount > 0" class="badge">TA 戳了你 {{ data.unreadPokeCount }} 次</div>
        </div>

        <!-- 对方今日打卡 -->
        <div class="card" style="margin-top: 14px" v-if="partner">
          <div class="card-title">TA 的今日打卡</div>
          <div v-if="partner.todayChecked" class="today-ok">✅ 今天已打卡</div>
          <div v-else class="today-no">⌛ 今天还没打卡，戳戳 TA 吧</div>
          <div class="mini-stats">
            <div><span class="n">{{ partner.currentStreak }}</span><span class="l">连续</span></div>
            <div><span class="n">{{ partner.totalDays }}</span><span class="l">累计</span></div>
            <div><span class="n">{{ partner.points }}</span><span class="l">积分</span></div>
          </div>
        </div>

        <!-- 共同统计 -->
        <div class="card" style="margin-top: 14px" v-if="summary">
          <div class="card-title">我们的共同记录</div>
          <div class="mini-stats">
            <div><span class="n">{{ summary.commonDays }}</span><span class="l">共同打卡</span></div>
            <div><span class="n">{{ summary.myStreak }}/{{ summary.partnerStreak }}</span><span class="l">各自连续</span></div>
            <div><span class="n">{{ summary.totalPoints }}</span><span class="l">合计积分</span></div>
          </div>
        </div>

        <!-- 对方日历 -->
        <div class="card" style="margin-top: 14px" v-if="cal">
          <div class="cal-head">
            <button class="cal-nav" @click="calPrev">‹</button>
            <div class="cal-month">TA 的 {{ calCur.y }}年{{ calCur.m }}月</div>
            <button class="cal-nav" @click="calNext">›</button>
          </div>
          <div class="cal-grid">
            <div v-for="w in weekdays" :key="w" class="cal-wk">{{ w }}</div>
            <div
              v-for="(d, i) in calCells"
              :key="i"
              :class="['cal-cell', !d ? 'empty' : '', d && d.status === 1 ? 'checked' : '', d && d.status === 2 ? 'makeup' : '']"
            >
              <template v-if="d">
                <span>{{ dayNum(d) }}</span>
                <span v-if="d.mood" class="cell-mood">{{ moodEmoji[d.mood] }}</span>
              </template>
            </div>
          </div>
        </div>

        <!-- 互动 -->
        <div class="row-actions" style="margin-top: 14px">
          <button class="btn btn-ghost" @click="quickPoke">👉 戳一下</button>
          <button class="btn" @click="openPoke">💌 留言督促</button>
        </div>

        <div class="card" style="margin-top: 14px" v-if="pokes.length">
          <div class="card-title">最近互动</div>
          <div v-for="p in pokes" :key="p.id" class="poke-item">
            <span class="poke-who">{{ p.fromMe ? '我' : (data.partner ? data.partner.nickname : 'TA') }}</span>
            <span class="poke-msg">{{ p.message || '戳了一下 👉' }}</span>
          </div>
        </div>

        <span class="link-danger" @click="doUnbind">解除关系</span>
      </template>
    </div>

    <!-- 留言弹层 -->
    <div v-if="showPoke" class="modal-mask" @click="showPoke = false">
      <div class="modal-card" @click.stop>
        <h3 class="modal-title">给 TA 留言</h3>
        <textarea class="note-input" v-model="pokeMsg" maxlength="200" placeholder="今天也要加油打卡哦～"></textarea>
        <div class="modal-actions">
          <button class="btn btn-ghost" @click="showPoke = false">取消</button>
          <button class="btn" @click="sendPoke">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.center { text-align: center; }
.big { font-size: 50px; }
.ctitle { font-size: 18px; font-weight: 700; margin-top: 6px; }
.csub { font-size: 13px; color: var(--c-muted); margin-top: 6px; }
.mycode {
  display: inline-flex; align-items: center; gap: 10px; margin-top: 16px;
  background: #EAF6EF; border-radius: 14px; padding: 10px 18px;
}
.code { font-size: 24px; font-weight: 800; letter-spacing: 3px; color: var(--c-primary-d); }
.copy { font-size: 12px; color: var(--c-primary-d); background: #fff; padding: 3px 10px; border-radius: 16px; }
.code-input { text-align: center; letter-spacing: 3px; font-size: 18px; }
.row-actions { display: flex; gap: 10px; margin-top: 18px; }
.row-actions .btn { flex: 1; }
.badge {
  display: inline-block; margin-top: 10px; background: #FFE9E6; color: #E06A5B;
  font-size: 12px; padding: 4px 12px; border-radius: 16px;
}
.card-title { font-size: 14px; font-weight: 700; margin-bottom: 12px; }
.today-ok { font-size: 15px; color: var(--c-primary-d); font-weight: 600; }
.today-no { font-size: 15px; color: var(--c-muted); }
.mini-stats { display: flex; margin-top: 14px; }
.mini-stats > div { flex: 1; text-align: center; display: flex; flex-direction: column; }
.mini-stats .n { font-size: 20px; font-weight: 800; color: var(--c-primary-d); }
.mini-stats .l { font-size: 12px; color: var(--c-muted); margin-top: 4px; }
.poke-item { display: flex; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--c-line); font-size: 14px; }
.poke-item:last-child { border-bottom: none; }
.poke-who { color: var(--c-primary-d); font-weight: 700; flex-shrink: 0; }
.poke-msg { color: var(--c-text); }
</style>
