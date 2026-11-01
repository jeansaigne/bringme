<script setup lang="ts">
import { statusLabels, statusStyle, type CourseStatus } from '../lib/status'

defineProps<{ status: CourseStatus; size?: 'sm' | 'md' | 'lg' }>()
</script>

<template>
  <UBadge
    :color="statusStyle[status].color"
    :icon="statusStyle[status].icon"
    variant="subtle"
    :size="size ?? 'md'"
    :label="statusLabels[status]"
  />
</template>
