<script setup lang="ts">
import { computed } from 'vue'
import { timeline, type CourseStatus } from '../lib/status'

const props = defineProps<{ status: CourseStatus }>()

const model = computed(() => timeline(props.status))
const items = computed(() => model.value.steps.map((s) => ({ value: s.key, title: s.title, icon: s.icon })))
const current = computed(() => model.value.steps[model.value.current]?.key)
const ended = computed(() => ['CANCELLED', 'EXPIRED', 'REFUND_PENDING', 'REFUNDED'].includes(props.status))
</script>

<template>
  <UTimeline
    :items="items"
    :model-value="current"
    orientation="horizontal"
    size="sm"
    :color="ended ? 'neutral' : 'primary'"
    class="w-full"
    :ui="{ title: 'text-xs sm:text-sm', item: 'flex-1' }"
  />
</template>
