<script setup lang="ts">
import { computed } from 'vue'
import type { Progress } from '@/types'

const props = defineProps<{ progress: Progress; label?: string }>()

const percent = computed(() =>
  props.progress.total === 0 ? 0 : Math.round((props.progress.completed / props.progress.total) * 100),
)
</script>

<template>
  <div class="progress">
    <div
      class="progress__track"
      role="progressbar"
      :aria-valuenow="percent"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="label ?? 'Progress'"
    >
      <div class="progress__fill" :style="{ width: `${percent}%` }" />
    </div>
    <span class="progress__text">
      {{ progress.total === 0 ? 'Not started' : `${progress.completed}/${progress.total} pages · ${percent}%` }}
    </span>
  </div>
</template>

<style scoped>
.progress {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.progress__track {
  flex: 1;
  height: 6px;
  min-width: 60px;
  background-color: var(--color-surface-active);
}

.progress__fill {
  height: 100%;
  background-color: var(--color-accent);
  transition: width var(--transition-medium);
}

.progress__text {
  flex-shrink: 0;
  font-size: 0.78rem;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}
</style>
