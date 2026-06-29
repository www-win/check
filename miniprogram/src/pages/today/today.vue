<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request, getStatus, getGoal, saveGoal, clearGoal, toast } from '../../utils/request'
import { BASE_URL } from '../../utils/config'

const status = ref(null)
const goal = ref(null)

const activeMethod = ref(null)
const mood = ref(null)
const note = ref('')
const imageUrl = ref('')
const submitting = ref(false)

const methods = [
  { key: 'normal', icon: '✅', label: '普通打卡', desc: '直接签到' },
  { key: 'photo', icon: '📷', label: '拍照打卡', desc: '带张照片' },
  { key: 'mood', icon: '😊', label: '心情打卡', desc: '记录心情' },
  { key: 'note', icon: '📝', label: '笔记打卡', desc: '写句话' }
]
const moods = [
  { v: 1, e: '😣' }, { v: 2, e: '😕' }, { v: 3, e: '😐' }, { v: 4, e: '🙂' }, { v: 5, e: '😄' }
]
const panelTitle = { photo: '拍照打卡', mood: '心情打卡', note: '笔记打卡' }

// 目标编辑弹层
const showEditor = ref(false)
const editContent = ref('')
const editDate = ref('')

onShow(() => {
  loadStatus()
  loadGoal()
})

function loadStatus() {
  getStatus().then((d) => (status.value = d)).catch((e) => toast(e.message))
}
function loadGoal() {
  getGoal().then((d) => (goal.value = d)).catch((e) => toast(e.message))
}

// 倒计时
const countdown = computed(() => {
  if (!goal.value || !goal.value.targetDate) return null
  const t = new Date(goal.value.targetDate + 'T00:00:00').getTime()
  const n = new Date()
  const today = new Date(n.getFullYear(), n.getMonth(), n.getDate()).getTime()
  const diff = Math.round((t - today) / 86400000)
  if (diff > 0) return '距目标还有 ' + diff + ' 天'
  if (diff === 0) return '今天就是目标日'
  return '已超 ' + -diff + ' 天，继续加油'
})

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
  if (key === 'photo' && !imageUrl.value) pickPhoto()
}

function doCheckin() {
  submitting.value = true
  request('/checkin', { method: 'POST', data: { mood: mood.value, note: note.value || null, imageUrl: imageUrl.value || null } })
    .then((d) => {
      if (d.milestone) toast('连续 ' + d.milestone + ' 天，额外奖励积分！')
      else toast('打卡成功，+' + d.pointsEarned + ' 积分')
      reset()
      loadStatus()
    })
    .catch((e) => toast(e.message))
    .finally(() => (submitting.value = false))
}

function pickPhoto() {
  uni.chooseImage({
    count: 1,
    success: (res) => {
      const fp = res.tempFilePaths[0]
      uni.showLoading({ title: '上传中' })
      uni.uploadFile({
        url: BASE_URL + '/api/checkin/upload',
        filePath: fp,
        name: 'file',
        header: { Authorization: 'Bearer ' + uni.getStorageSync('token') },
        success: (up) => {
          uni.hideLoading()
          try {
            const body = JSON.parse(up.data)
            if (body.code === 0) {
              imageUrl.value = body.data.url
              toast('照片已上传')
            } else if (body.code === 50001) {
              toast('图片功能需配置 OSS，可先不带照片打卡')
            } else {
              toast(body.msg)
            }
          } catch (e) {
            toast('上传失败')
          }
        },
        fail: () => {
          uni.hideLoading()
          toast('上传失败')
        }
      })
    }
  })
}

// 目标编辑
function openEditor() {
  editContent.value = goal.value ? goal.value.content : ''
  editDate.value = goal.value && goal.value.targetDate ? goal.value.targetDate : ''
  showEditor.value = true
}
function onDateChange(e) {
  editDate.value = e.detail.value
}
function saveGoalNow() {
  if (!editContent.value.trim()) return toast('请填写目标内容')
  saveGoal({ content: editContent.value.trim(), targetDate: editDate.value || null })
    .then(() => {
      toast('目标已保存')
      showEditor.value = false
      loadGoal()
    })
    .catch((e) => toast(e.message))
}
function removeGoal() {
  uni.showModal({
    title: '提示',
    content: '确定清除当前目标？',
    success: (r) => {
      if (!r.confirm) return
      clearGoal().then(() => {
        toast('目标已清除')
        showEditor.value = false
        loadGoal()
      }).catch((e) => toast(e.message))
    }
  })
}
</script>

<template>
  <view class="page-body">
    <!-- 目标卡 -->
    <view class="goalcard">
      <block v-if="goal">
        <view class="goal-head">
          <text class="goal-label">🎯 我的学习目标</text>
          <text class="goal-edit" @tap="openEditor">编辑</text>
        </view>
        <view class="goal-text">{{ goal.content }}</view>
        <view v-if="countdown" class="goal-cd">⏳ {{ countdown }}</view>
      </block>
      <view v-else class="goal-empty" @tap="openEditor">＋ 设定你的学习目标</view>
    </view>

    <!-- 统计 -->
    <view class="statcard">
      <view class="stat">
        <view class="stat-num">{{ status ? status.currentStreak : 0 }}</view>
        <view class="stat-lb">🔥 连续天数</view>
      </view>
      <view class="stat">
        <view class="stat-num">{{ status ? status.totalDays : 0 }}</view>
        <view class="stat-lb">累计打卡</view>
      </view>
      <view class="stat">
        <view class="stat-num">{{ status ? status.points : 0 }}</view>
        <view class="stat-lb">积分</view>
      </view>
    </view>

    <!-- 已打卡 -->
    <view v-if="status && status.todayChecked" class="checked">
      <view class="checked-ic">✓</view>
      <view class="checked-main">今日已完成打卡</view>
      <view class="checked-sub">明天再来，保持连续 🔥</view>
    </view>

    <!-- 选择打卡方式 -->
    <block v-else>
      <view class="section-title">选择打卡方式</view>
      <view class="methods">
        <view
          v-for="m in methods"
          :key="m.key"
          :class="['method', { active: activeMethod === m.key }]"
          @tap="selectMethod(m.key)"
        >
          <view class="method-ic">{{ m.icon }}</view>
          <view class="method-lb">{{ m.label }}</view>
          <view class="method-desc">{{ m.desc }}</view>
        </view>
      </view>

      <view v-if="activeMethod" class="panel">
        <view class="panel-title">{{ panelTitle[activeMethod] }}</view>

        <view v-if="activeMethod === 'mood'" class="moods">
          <view
            v-for="m in moods"
            :key="m.v"
            :class="['mood', { on: mood === m.v }]"
            @tap="mood = mood === m.v ? null : m.v"
          >{{ m.e }}</view>
        </view>

        <textarea
          v-else-if="activeMethod === 'note'"
          class="note"
          v-model="note"
          maxlength="200"
          placeholder="记录一下今天的状态…"
        />

        <block v-else-if="activeMethod === 'photo'">
          <view class="photo-btn" @tap="pickPhoto">📷 {{ imageUrl ? '重新选择' : '添加照片' }}</view>
          <image v-if="imageUrl" :src="imageUrl" class="photo-thumb" mode="widthFix" />
        </block>

        <button class="btn finish" hover-class="btn-hover" :disabled="submitting" @tap="doCheckin">
          {{ submitting ? '打卡中…' : '完成打卡' }}
        </button>
      </view>
    </block>

    <!-- 目标编辑弹层 -->
    <view v-if="showEditor" class="mask" @tap="showEditor = false">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">{{ goal ? '编辑学习目标' : '设定学习目标' }}</view>
        <view class="field-label">目标内容</view>
        <textarea class="note" v-model="editContent" maxlength="200" placeholder="例如：每天背 50 个单词，三个月内考过雅思 7 分" />
        <view class="field-label">目标日期（可选）</view>
        <picker mode="date" :value="editDate" @change="onDateChange">
          <view class="picker">{{ editDate || '点击选择日期' }}</view>
        </picker>
        <view class="sheet-actions">
          <button class="btn btn-ghost flex1" @tap="showEditor = false">取消</button>
          <button class="btn flex1" hover-class="btn-hover" @tap="saveGoalNow">保存</button>
        </view>
        <view v-if="goal" class="link-danger" @tap="removeGoal">清除目标</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
/* 目标卡 */
.goalcard { background: linear-gradient(135deg, #EAF7EF, #DCF1E4); border-radius: 32rpx; padding: 28rpx 32rpx; margin-bottom: 24rpx; }
.goal-head { display: flex; justify-content: space-between; align-items: center; }
.goal-label { font-size: 24rpx; color: var(--c-primary-d); font-weight: 700; }
.goal-edit { font-size: 24rpx; color: var(--c-primary-d); background: rgba(255,255,255,.7); padding: 6rpx 20rpx; border-radius: 30rpx; }
.goal-text { font-size: 32rpx; font-weight: 600; margin-top: 14rpx; line-height: 1.4; }
.goal-cd { display: inline-block; margin-top: 16rpx; background: #fff; color: var(--c-primary-d); font-size: 26rpx; font-weight: 700; padding: 8rpx 22rpx; border-radius: 30rpx; }
.goal-empty { text-align: center; color: var(--c-primary-d); font-size: 30rpx; font-weight: 600; padding: 12rpx 0; }

/* 统计 */
.statcard { display: flex; background: #fff; border-radius: 32rpx; box-shadow: 0 4rpx 16rpx rgba(45,140,85,.08); overflow: hidden; }
.stat { flex: 1; text-align: center; padding: 30rpx 0; }
.stat + .stat { border-left: 2rpx solid var(--c-line); }
.stat-num { font-size: 48rpx; font-weight: 800; color: var(--c-primary-d); }
.stat-lb { font-size: 24rpx; color: var(--c-muted); margin-top: 8rpx; }

/* 已打卡 */
.checked { margin-top: 28rpx; background: linear-gradient(135deg, #6FD299, #3DBA6F); border-radius: 32rpx; padding: 50rpx 0; text-align: center; color: #fff; }
.checked-ic { font-size: 84rpx; }
.checked-main { font-size: 36rpx; font-weight: 700; margin-top: 12rpx; }
.checked-sub { font-size: 26rpx; opacity: .92; margin-top: 8rpx; }

/* 方式 */
.section-title { font-size: 30rpx; font-weight: 700; margin: 36rpx 4rpx 20rpx; }
.methods { display: flex; flex-wrap: wrap; justify-content: space-between; }
.method { width: 48%; box-sizing: border-box; background: #fff; border-radius: 28rpx; padding: 28rpx; margin-bottom: 22rpx; border: 4rpx solid transparent; box-shadow: 0 4rpx 16rpx rgba(45,140,85,.06); }
.method.active { border-color: var(--c-primary); background: #F3FBF6; }
.method-ic { font-size: 52rpx; }
.method-lb { font-size: 30rpx; font-weight: 700; margin-top: 10rpx; }
.method-desc { font-size: 24rpx; color: var(--c-muted); margin-top: 4rpx; }

.panel { background: #fff; border-radius: 32rpx; padding: 32rpx; box-shadow: 0 4rpx 16rpx rgba(45,140,85,.06); }
.panel-title { font-size: 28rpx; font-weight: 700; margin-bottom: 20rpx; }
.moods { display: flex; justify-content: space-between; }
.mood { width: 96rpx; height: 88rpx; line-height: 88rpx; text-align: center; font-size: 44rpx; background: #F4F8F5; border-radius: 22rpx; border: 4rpx solid transparent; }
.mood.on { background: #EAF6EF; border-color: var(--c-primary-l); }
.note { width: 100%; min-height: 140rpx; box-sizing: border-box; padding: 20rpx 24rpx; border: 2rpx solid var(--c-line); border-radius: 24rpx; font-size: 28rpx; background: #fff; }
.photo-btn { height: 92rpx; line-height: 92rpx; text-align: center; border: 2rpx dashed var(--c-line); border-radius: 22rpx; color: var(--c-muted); font-size: 28rpx; }
.photo-thumb { width: 100%; border-radius: 24rpx; margin-top: 18rpx; }
.finish { margin-top: 28rpx; }

/* 弹层 */
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(20,32,26,.45); display: flex; align-items: flex-end; z-index: 100; }
.sheet { width: 100%; background: #fff; border-radius: 36rpx 36rpx 0 0; padding: 36rpx 36rpx 48rpx; }
.sheet-title { font-size: 36rpx; font-weight: 700; text-align: center; margin-bottom: 24rpx; }
.picker { height: 92rpx; line-height: 92rpx; padding: 0 28rpx; border: 2rpx solid var(--c-line); border-radius: 24rpx; color: var(--c-text); font-size: 30rpx; }
.sheet-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.flex1 { flex: 1; }
.link-danger { text-align: center; color: #E06A5B; font-size: 26rpx; margin-top: 26rpx; }
</style>
