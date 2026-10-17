<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import Markdown from '@/components/Markdown.vue'
import TaskFrame, { type TaskState } from './TaskFrame.vue'
import type { MultipleChoiceBlock } from '@/types'

const props = defineProps<{ block: MultipleChoiceBlock }>()
const emit = defineEmits<{ solved: [] }>()

const name = useId()
const multiple = computed(() => props.block.correct.length > 1)
const selected = ref<number[]>([])
const state = ref<TaskState>('open')

function select(index: number, checked: boolean) {
  if (!multiple.value) selected.value = [index]
  else if (checked) selected.value = [...selected.value, index]
  else selected.value = selected.value.filter((i) => i !== index)
  if (state.value === 'wrong') state.value = 'open'
}

function check() {
  const chosen = [...selected.value].sort()
  const correct = [...props.block.correct].sort()
  if (chosen.length === correct.length && chosen.every((value, i) => value === correct[i])) {
    state.value = 'solved'
    emit('solved')
  } else {
    state.value = 'wrong'
  }
}

/** After a check: what to say about each option. Only revealed/solved show the right ones. */
function mark(index: number): 'right' | 'wrong' | null {
  const isCorrect = props.block.correct.includes(index)
  if (state.value === 'solved' || state.value === 'revealed') return isCorrect ? 'right' : null
  if (state.value === 'wrong' && selected.value.includes(index) && !isCorrect) return 'wrong'
  return null
}
</script>

<template>
  <TaskFrame
    :state="state"
    :explanation="block.explanation"
    :hint="multiple ? 'Select all that apply.' : 'Select one.'"
    @check="check"
    @reveal="state = 'revealed'"
  >
    <Markdown :text="block.question" />
    <fieldset class="choices">
      <legend class="choices__legend">Options</legend>
      <label
        v-for="(option, index) in block.options"
        :key="index"
        class="choice"
        :class="mark(index) && `choice--${mark(index)}`"
      >
        <input
          :type="multiple ? 'checkbox' : 'radio'"
          :name="name"
          :checked="selected.includes(index)"
          :disabled="state === 'solved'"
          @change="select(index, ($event.target as HTMLInputElement).checked)"
        />
        <Markdown :text="option" inline />
        <span v-if="mark(index) === 'right'" class="choice__note">correct</span>
        <span v-else-if="mark(index) === 'wrong'" class="choice__note">wrong</span>
      </label>
    </fieldset>
  </TaskFrame>
</template>

<style scoped>
.choices {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  border: none;
}

.choices__legend {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.choice {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.choice:hover {
  background-color: var(--color-surface-hover);
}

.choice input {
  flex-shrink: 0;
  accent-color: var(--color-accent);
}

.choice--right {
  border-color: var(--color-status-good);
}

.choice--wrong {
  border: 2px solid var(--color-status-critical);
}

.choice__note {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}
</style>
