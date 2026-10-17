<script setup lang="ts">
import Markdown from '@/components/Markdown.vue'

/**
 * open: not checked yet. wrong: last check failed. solved: all correct (counts
 * for progress). revealed: the learner asked for the solution; it's shown, but
 * the task still has to be answered correctly to count.
 */
export type TaskState = 'open' | 'wrong' | 'solved' | 'revealed'

defineProps<{ state: TaskState; explanation: string; hint?: string }>()
const emit = defineEmits<{ check: []; reveal: [] }>()
</script>

<template>
  <section class="task" :class="`task--${state}`">
    <span class="task__label">Task</span>
    <slot />

    <div class="task__footer">
      <button type="button" class="task__check" :disabled="state === 'solved'" @click="emit('check')">
        {{ state === 'solved' ? 'Solved' : 'Check' }}
      </button>
      <button v-if="state === 'wrong'" type="button" class="task__reveal" @click="emit('reveal')">Show solution</button>
      <span v-if="state === 'wrong'" class="task__feedback task__feedback--wrong" role="status">
        Not quite. Check the marked fields and try again.
      </span>
      <span v-else-if="state === 'solved'" class="task__feedback task__feedback--solved" role="status">Correct.</span>
      <span v-else-if="state === 'revealed'" class="task__feedback" role="status">
        Solution shown. Enter it to complete the task.
      </span>
      <span v-else-if="hint" class="task__hint">{{ hint }}</span>
    </div>

    <div v-if="(state === 'solved' || state === 'revealed') && explanation" class="task__explanation">
      <Markdown :text="explanation" />
    </div>
  </section>
</template>

<style scoped>
.task {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-accent);
}

.task--solved {
  border-left-color: var(--color-status-good);
}

.task__label {
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.task__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.task__check,
.task__reveal {
  padding: 8px 14px;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 600;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.task__check {
  color: var(--color-text-on-accent);
  background-color: var(--color-accent);
  border: 1px solid var(--color-accent);
}

.task__check:hover:not(:disabled) {
  background-color: var(--color-accent-hover);
}

.task__check:disabled {
  cursor: default;
  color: var(--color-text-secondary);
  background-color: transparent;
  border-color: var(--color-border);
}

.task__reveal {
  color: var(--color-text-primary);
  background-color: transparent;
  border: 1px solid var(--color-border);
}

.task__reveal:hover {
  background-color: var(--color-surface-hover);
}

.task__check:focus-visible,
.task__reveal:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 1px;
}

.task__feedback,
.task__hint {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.task__feedback--solved {
  font-weight: 600;
  color: var(--color-status-good);
}

.task__feedback--wrong {
  color: var(--color-text-primary);
}

.task__explanation {
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
</style>
