<script setup lang="ts">
import { ref } from 'vue'
import Markdown from '@/components/Markdown.vue'
import AnswerInput from './AnswerInput.vue'
import TaskFrame, { type TaskState } from './TaskFrame.vue'
import { isCorrect, solution } from '@/answers'
import type { TableBlock } from '@/types'

const props = defineProps<{ block: TableBlock }>()
const emit = defineEmits<{ solved: [] }>()

const inputs = ref<string[][]>(props.block.rows.map((row) => row.map(() => '')))
const results = ref<('right' | 'wrong' | null)[][]>(props.block.rows.map((row) => row.map(() => null)))
const state = ref<TaskState>('open')

function check() {
  results.value = props.block.rows.map((row, r) =>
    row.map((cell, c) => (cell.given !== null ? null : isCorrect(cell, inputs.value[r]?.[c] ?? '') ? 'right' : 'wrong')),
  )
  if (results.value.every((row) => row.every((result) => result !== 'wrong'))) {
    state.value = 'solved'
    emit('solved')
  } else if (state.value !== 'revealed') {
    state.value = 'wrong'
  }
}
</script>

<template>
  <TaskFrame :state="state" :explanation="block.explanation" @check="check" @reveal="state = 'revealed'">
    <Markdown v-if="block.instruction" :text="block.instruction" />
    <!-- Wide tableaus scroll sideways instead of squeezing on phones. -->
    <div class="table-scroll">
      <table class="table">
        <thead>
          <tr>
            <th v-for="(column, c) in block.columns" :key="c" scope="col"><Markdown :text="column" inline /></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, r) in block.rows" :key="r">
            <td v-for="(cell, c) in row" :key="c">
              <Markdown v-if="cell.given !== null" :text="cell.given" inline />
              <AnswerInput
                v-else
                v-model="inputs[r]![c]!"
                :result="results[r]?.[c] ?? null"
                :solution="state === 'revealed' ? solution(cell) : null"
                :label="`Row ${r + 1}, ${block.columns[c] ?? `column ${c + 1}`}`"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </TaskFrame>
</template>

<style scoped>
.table-scroll {
  overflow-x: auto;
}

.table {
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}

.table th,
.table td {
  padding: 6px 10px;
  text-align: center;
  white-space: nowrap;
  border: 1px solid var(--color-border);
}

.table th {
  font-weight: 600;
  background-color: var(--color-surface-hover);
}

.table td :deep(.answer__input) {
  width: 4.5em;
  text-align: center;
}
</style>
