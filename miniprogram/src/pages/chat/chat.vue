<script setup>
import { ref, nextTick } from 'vue'
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { getMessages, sendMessage, markChatRead, toast } from '../../utils/request'

const POLL_MS = 3000
const peerId = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scrollTop = ref(0)
let timer = null

onLoad((q) => {
  peerId.value = Number(q.peerId)
  uni.setNavigationBarTitle({ title: q.name ? decodeURIComponent(q.name) : '聊天' })
  firstLoad()
})

// 页面显示时启动轮询(切前台/首次进入);先兜底清理避免定时器叠加
onShow(() => {
  if (timer) clearInterval(timer)
  if (peerId.value) timer = setInterval(poll, POLL_MS)
})

// 切后台/被上层页面覆盖时停止轮询,避免空转耗电与无谓请求
onHide(() => { if (timer) { clearInterval(timer); timer = null } })

onUnload(() => { if (timer) { clearInterval(timer); timer = null } })

function maxId() {
  return messages.value.length ? messages.value[messages.value.length - 1].id : 0
}

function scrollToBottom() {
  nextTick(() => { scrollTop.value = messages.value.length * 100000 })
}

function firstLoad() {
  getMessages(peerId.value).then((list) => {
    messages.value = list || []
    scrollToBottom()
    if (messages.value.length) markChatRead(peerId.value)
  }).catch((e) => toast(e.message))
}

function poll() {
  getMessages(peerId.value, maxId()).then((list) => {
    if (list && list.length) {
      messages.value = messages.value.concat(list)
      scrollToBottom()
      markChatRead(peerId.value)
    }
  }).catch(() => {})
}

function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  sending.value = true
  sendMessage(peerId.value, text).then((msg) => {
    messages.value.push(msg)
    draft.value = ''
    scrollToBottom()
  }).catch((e) => toast(e.message)).finally(() => { sending.value = false })
}
</script>

<template>
  <view class="chat-page">
    <scroll-view class="msg-list" scroll-y :scroll-top="scrollTop" scroll-with-animation>
      <view v-if="!messages.length" class="empty">打个招呼吧 👋</view>
      <view v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.mine }">
        <view class="bubble">{{ m.content }}</view>
      </view>
    </scroll-view>
    <view class="input-bar">
      <input class="input" v-model="draft" placeholder="说点什么..." confirm-type="send" @confirm="send" />
      <text class="send-btn" :class="{ disabled: !draft.trim() || sending }" @tap="send">发送</text>
    </view>
  </view>
</template>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100vh; background: #F1FAF4; }
.msg-list { flex: 1; padding: 20rpx; box-sizing: border-box; }
.empty { text-align: center; color: var(--c-muted, #8A9A90); font-size: 26rpx; padding: 60rpx 0; }
.msg-row { display: flex; margin-bottom: 20rpx; }
.msg-row.mine { justify-content: flex-end; }
.bubble { max-width: 70%; padding: 18rpx 24rpx; border-radius: 20rpx; font-size: 30rpx;
  background: #fff; color: #222; word-break: break-all; }
.msg-row.mine .bubble { background: #2E9E5B; color: #fff; }
.input-bar { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 20rpx;
  background: #fff; border-top: 1rpx solid #E5EDE8; }
.input { flex: 1; height: 72rpx; padding: 0 24rpx; background: #F0F2F1; border-radius: 36rpx; font-size: 30rpx; }
.send-btn { padding: 0 32rpx; height: 72rpx; line-height: 72rpx; border-radius: 36rpx;
  background: #2E9E5B; color: #fff; font-size: 28rpx; }
.send-btn.disabled { background: #B8C6BE; }
</style>
