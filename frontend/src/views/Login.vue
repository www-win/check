<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, setAuth } from '../api'
import { toast } from '../toast'

const router = useRouter()
const phone = ref('')
const code = ref('')
const sending = ref(false)
const counting = ref(0)
const logging = ref(false)
let timer

function startCount() {
  counting.value = 60
  timer = setInterval(() => {
    if (--counting.value <= 0) clearInterval(timer)
  }, 1000)
}
onUnmounted(() => clearInterval(timer))

async function sendCode() {
  if (!/^\d{11}$/.test(phone.value)) return toast('请输入 11 位手机号')
  sending.value = true
  try {
    await request('/auth/send-code', { method: 'POST', body: { phone: phone.value } })
    toast('验证码已发送')
    startCount()
  } catch (e) {
    toast(e.message)
  } finally {
    sending.value = false
  }
}

async function login() {
  if (!/^\d{11}$/.test(phone.value)) return toast('请输入 11 位手机号')
  if (!code.value) return toast('请输入验证码')
  logging.value = true
  try {
    const d = await request('/auth/login', { method: 'POST', body: { phone: phone.value, code: code.value } })
    setAuth(d)
    router.push('/today')
  } catch (e) {
    toast(e.message)
  } finally {
    logging.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <div class="brand">
      <div class="logo">🌱</div>
      <h1>学伴打卡</h1>
      <p>每天一点点，坚持看得见</p>
    </div>

    <input class="input" v-model="phone" type="tel" maxlength="11" placeholder="请输入手机号" />

    <div class="code-row mt">
      <input class="input" v-model="code" type="tel" maxlength="6" placeholder="验证码" />
      <button class="code-btn" :disabled="sending || counting > 0" @click="sendCode">
        {{ counting > 0 ? counting + 's' : '获取验证码' }}
      </button>
    </div>

    <button class="btn mt" :disabled="logging" @click="login">
      {{ logging ? '登录中…' : '登录 / 注册' }}
    </button>

    <p class="login-tip">
      首次登录将自动注册<br />
      开发环境下短信验证码打印在后端控制台
    </p>
  </div>
</template>
