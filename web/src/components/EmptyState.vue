<script setup lang="ts">
defineProps<{ icon: string; title: string; description?: string }>()
</script>

<template>
  <div class="flex flex-col items-center rounded-lg border border-dashed border-accented px-6 py-10 text-center">
    <span class="mb-3 inline-flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
      <UIcon
        :name="icon"
        class="size-6"
      />
    </span>
    <p class="font-semibold text-highlighted">
      {{ title }}
    </p>
    <p
      v-if="description"
      class="mt-1 max-w-sm text-sm text-muted"
    >
      {{ description }}
    </p>
    <div class="mt-4 empty:hidden">
      <slot />
    </div>
  </div>
</template>
