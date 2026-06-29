<script setup>
defineProps({
  streak: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  points: { type: Number, default: 0 }
})
</script>

<template>
  <div class="statcard">
    <div class="stat">
      <div class="stat-num">{{ streak }}</div>
      <div class="stat-lb">🔥 连续天数</div>
    </div>
    <div class="stat">
      <div class="stat-num">{{ total }}</div>
      <div class="stat-lb">累计打卡</div>
    </div>
    <div class="stat">
      <div class="stat-num">{{ points }}</div>
      <div class="stat-lb">积分</div>
    </div>
  </div>
</template>
