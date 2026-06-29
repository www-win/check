<script setup>
import { ref } from 'vue'
import { saveGoal, clearGoal } from '../api'
import { toast } from '../toast'

const props = defineProps({
  goal: { type: Object, default: null }
})
const emit = defineEmits(['saved', 'close'])

const content = ref(props.goal ? props.goal.content : '')
const targetDate = ref(props.goal && props.goal.targetDate ? props.goal.targetDate : '')
const saving = ref(false)
const removing = ref(false)

async function save() {
  if (!content.value.trim()) return toast('请填写目标内容')
  saving.value = true
  try {
    await saveGoal({ content: content.value.trim(), targetDate: targetDate.value || null })
    toast('目标已保存')
    emit('saved')
  } catch (e) {
    toast(e.message)
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!confirm('确定清除当前目标？')) return
  removing.value = true
  try {
    await clearGoal()
    toast('目标已清除')
    emit('saved')
  } catch (e) {
    toast(e.message)
  } finally {
    removing.value = false
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="$emit('close')">
    <div class="modal-card">
      <p class="modal-title">{{ goal ? '编辑学习目标' : '设定学习目标' }}</p>

      <div class="field-label" style="margin-top: 0">目标内容</div>
      <textarea
        class="note-input"
        v-model="content"
        maxlength="200"
        placeholder="例如：每天背 50 个单词，三个月内考过雅思 7 分"
      ></textarea>

      <div class="field-label">目标日期（可选）</div>
      <input class="input" type="date" v-model="targetDate" />

      <div class="modal-actions">
        <button class="btn btn-ghost" :disabled="saving" @click="$emit('close')">取消</button>
        <button class="btn" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </div>

      <button v-if="goal" class="link-danger" :disabled="removing" @click="remove">清除目标</button>
    </div>
  </div>
</template>
