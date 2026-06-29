<script setup>
const props = defineProps({
  modelValue: { type: Number, default: null }
})
const emit = defineEmits(['update:modelValue'])

const moods = [
  { v: 1, e: '😣' },
  { v: 2, e: '😕' },
  { v: 3, e: '😐' },
  { v: 4, e: '🙂' },
  { v: 5, e: '😄' }
]

function pick(v) {
  emit('update:modelValue', props.modelValue === v ? null : v)
}
</script>

<template>
  <div class="moodpicker">
    <button
      v-for="m in moods"
      :key="m.v"
      type="button"
      :class="['mood', { on: modelValue === m.v }]"
      @click="pick(m.v)"
    >
      {{ m.e }}
    </button>
  </div>
</template>
