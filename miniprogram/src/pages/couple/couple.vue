<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  getCouple, bindCouple, acceptCouple, rejectCouple,
  cancelCouple, unbindCouple, toast
} from '../../utils/request'

const data = ref(null)        // CoupleStatusResp
const inputCode = ref('')
const submitting = ref(false)

onShow(() => load())

function load() {
  getCouple().then((d) => (data.value = d)).catch((e) => toast(e.message))
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

      <!-- 已建立：对方内容在 Task 6 补全 -->
      <block v-else-if="data.status === 'ACTIVE'">
        <view class="card center">
          <view class="big">💞</view>
          <view class="title">你和 {{ data.partner ? data.partner.nickname : 'TA' }} 已是情侣</view>
        </view>
        <!-- ACTIVE-CONTENT-SLOT (Task 6 在此插入对方打卡/统计/互动) -->
        <view class="link-danger" @tap="doUnbind">解除关系</view>
      </block>
    </block>
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
</style>
