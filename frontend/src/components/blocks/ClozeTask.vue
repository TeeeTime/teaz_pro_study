<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import Markdown from '@/components/Markdown.vue'
import AnswerInput from './AnswerInput.vue'
import TaskFrame, { type TaskState } from './TaskFrame.vue'
import { renderMarkdown } from '@/markdown'
import { isCorrect, solution } from '@/answers'
import type { ClozeBlock } from '@/types'

const props = defineProps<{ block: ClozeBlock }>()
const emit = defineEmits<{ solved: [] }>()

/*
 * Gaps can sit anywhere in the Markdown (lists, tables, next to formulas), so the
 * text is rendered whole with an empty, id'd slot per gap, and each input is
 * teleported into its slot. The ids are this component's own (useId), and the
 * slot markup is inserted by us after rendering: markdown-it never sees HTML.
 */
const uid = useId()
const MARKER = 'GAPSLOTMARKER'
const html = computed(() =>
  renderMarkdown(props.block.text.replace(/\{\{(\d+)\}\}/g, `${MARKER}$1${MARKER}`)).replace(
    new RegExp(`${MARKER}(\\d+)${MARKER}`, 'g'),
    (_, n) => `<span class="cloze__slot" id="${uid}-gap-${n}"></span>`,
  ),
)

const inputs = ref<string[]>(props.block.gaps.map(() => ''))
const results = ref<('right' | 'wrong' | null)[]>(props.block.gaps.map(() => null))
const state = ref<TaskState>('open')

function check() {
  results.value = props.block.gaps.map((gap, i) => (isCorrect(gap, inputs.value[i] ?? '') ? 'right' : 'wrong'))
  if (results.value.every((r) => r === 'right')) {
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
    <div class="cloze markdown" v-html="html" />
    <Teleport v-for="(gap, i) in block.gaps" :key="i" defer :to="`#${uid}-gap-${i + 1}`">
      <AnswerInput
        v-model="inputs[i]!"
        :result="results[i] ?? null"
        :solution="state === 'revealed' ? solution(gap) : null"
        :label="`Gap ${i + 1}`"
      />
    </Teleport>
  </TaskFrame>
</template>

<style scoped>
.cloze {
  line-height: 2.1;
  overflow-wrap: anywhere;
}

.cloze :deep(p) {
  margin: 0 0 0.5em;
}

.cloze :deep(p:last-child) {
  margin-bottom: 0;
}

.cloze :deep(.katex-display) {
  overflow-x: auto;
}
</style>
