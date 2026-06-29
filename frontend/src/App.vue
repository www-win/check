<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TabBar from './components/TabBar.vue'

const route = useRoute()
const showTab = computed(() => route.path !== '/login')
</script>

<template>
  <div class="phone">
    <main class="phone-body">
      <router-view />
    </main>
    <TabBar v-if="showTab" />
  </div>
</template>
