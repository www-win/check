<script setup>
import { ref, onMounted } from 'vue'
import { request, BizError, getGoal } from '../api'
import { toast } from '../toast'
import StatCard from '../components/StatCard.vue'
import MoodPicker from '../components/MoodPicker.vue'
import GoalCard from '../components/GoalCard.vue'
import GoalEditor from '../components/GoalEditor.vue'

const status = ref(null)
const goal = ref(null)
const showGoalEditor = ref(false)
const activeMethod = ref(null) // 'photo' | 'mood' | 'note' | null
const mood = ref(null)
const note = ref('')
const imageUrl = ref('')
const uploading = ref(false)
const submitting = ref(false)
const fileInput = ref(null)

const methods = [
  { key: 'normal', icon: '✅', label: '普通打卡', desc: '直接签到' },
  { key: 'photo', icon: '📷', label: '拍照打卡', desc: '带张照片' },
  { key: 'mood', icon: '😊', label: '心情打卡', desc: '记录心情' },
  { key: 'note', icon: '📝', label: '笔记打卡', desc: '写句话' }
]
const panelTitle = { photo: '拍照打卡', mood: '心情打卡', note: '笔记打卡' }

async function load() {
  try {
    status.value = await request('/checkin/status')
  } catch (e) {
    toast(e.message)
  }
}
async function loadGoal() {
  try {
    goal.value = await getGoal()
  } catch (e) {
    toast(e.message)
  }
}
onMounted(() => {
  load()
  loadGoal()
})

function onGoalSaved() {
  showGoalEditor.value = false
  loadGoal()
}

function reset() {
  activeMethod.value = null
  mood.value = null
  note.value = ''
  imageUrl.value = ''
}

function selectMethod(key) {
  if (status.value && status.value.todayChecked) return
  if (key === 'normal') {
    reset()
    doCheckin()
    return
  }
  activeMethod.value = key
  if (key === 'photo' && !imageUrl.value) pickFile()
}

async function doCheckin() {
  submitting.value = true
  try {
    const d = await request('/checkin', {
      method: 'POST',
      body: { mood: mood.value, note: note.value || null, imageUrl: imageUrl.value || null }
    })
    if (d.milestone) toast('🎉 连续 ' + d.milestone + ' 天，额外奖励积分！')
    else toast('打卡成功，+' + d.pointsEarned + ' 积分')
    reset()
    await load()
  } catch (e) {
    toast(e.message)
  } finally {
    submitting.value = false
  }
}

async function cancelCheckin() {
  if (!window.confirm('撤销后本次所得积分将退回,确定吗?')) return
  submitting.value = true
  try {
    await request('/checkin/cancel', { method: 'POST' })
    toast('已撤销,可以重新打卡')
    await load()
  } catch (e) {
    toast(e.message)
  } finally {
    submitting.value = false
  }
}

function pickFile() {
  if (fileInput.value) fileInput.value.click()
}

async function onFile(e) {
  const f = e.target.files[0]
  if (!f) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(f.type)) {
    e.target.value = ''
    return toast('仅支持 jpg/png/webp')
  }
  if (f.size > 5 * 1024 * 1024) {
    e.target.value = ''
    return toast('图片不能超过 5MB')
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', f)
    const d = await request('/checkin/upload', { method: 'POST', body: fd, raw: true })
    imageUrl.value = d.url
    toast('照片已上传')
  } catch (err) {
    if (err instanceof BizError && err.code === 50001) {
      toast('图片功能需配置 OSS，可先不带照片打卡')
    } else {
      toast(err.message)
    }
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">今日打卡</h2>
      <p class="page-sub">
        {{ status && status.todayChecked ? '今天已经完成啦，继续保持～' : '选择一种方式，完成今天的打卡' }}
      </p>
    </div>

    <div class="page" style="padding-top: 8px">
      <GoalCard :goal="goal" @edit="showGoalEditor = true" style="margin-bottom: 14px" />

      <StatCard
        :streak="status ? status.currentStreak : 0"
        :total="status ? status.totalDays : 0"
        :points="status ? status.points : 0"
      />

      <!-- 已打卡 -->
      <div v-if="status && status.todayChecked" class="checked-banner">
        <div class="cb-ic">✓</div>
        <div class="cb-main">今日已完成打卡</div>
        <div class="cb-sub">明天再来,保持连续 🔥</div>
        <button class="cb-redo" type="button" :disabled="submitting" @click="cancelCheckin">打错了?撤销重打</button>
      </div>

      <!-- 未打卡：选择打卡方式 -->
      <template v-else>
        <div class="section-title">选择打卡方式</div>
        <div class="methods">
          <button
            v-for="m in methods"
            :key="m.key"
            type="button"
            :class="['method-card', { active: activeMethod === m.key }]"
            :disabled="submitting"
            @click="selectMethod(m.key)"
          >
            <span class="method-ic">{{ m.icon }}</span>
            <span class="method-lb">{{ m.label }}</span>
            <span class="method-desc">{{ m.desc }}</span>
          </button>
        </div>

        <!-- 选中某种方式后的输入面板 -->
        <div v-if="activeMethod" class="method-panel">
          <p class="panel-title">{{ panelTitle[activeMethod] }}</p>

          <template v-if="activeMethod === 'mood'">
            <MoodPicker v-model="mood" />
          </template>

          <template v-else-if="activeMethod === 'note'">
            <textarea class="note-input" v-model="note" maxlength="200" placeholder="记录一下今天的状态…"></textarea>
          </template>

          <template v-else-if="activeMethod === 'photo'">
            <button class="photo-btn" :disabled="uploading" @click="pickFile">
              📷 {{ uploading ? '上传中…' : (imageUrl ? '重新选择' : '添加照片') }}
            </button>
            <img v-if="imageUrl" :src="imageUrl" class="photo-thumb" alt="打卡照片" />
          </template>

          <button class="btn" style="margin-top: 16px" :disabled="submitting" @click="doCheckin">
            {{ submitting ? '打卡中…' : '完成打卡' }}
          </button>
        </div>

        <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="onFile" />
      </template>
    </div>

    <GoalEditor
      v-if="showGoalEditor"
      :goal="goal"
      @saved="onGoalSaved"
      @close="showGoalEditor = false"
    />
  </div>
</template>

<style scoped>
.cb-redo {
  margin-top: 14px;
  background: rgba(255, 255, 255, 0.85);
  color: #2D8C55;
  border: none;
  border-radius: 20px;
  padding: 8px 22px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.cb-redo:disabled { opacity: 0.6; cursor: default; }
</style>
