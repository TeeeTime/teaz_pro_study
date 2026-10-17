<script setup lang="ts">
import Markdown from '@/components/Markdown.vue'
import MultipleChoiceTask from './MultipleChoiceTask.vue'
import ClozeTask from './ClozeTask.vue'
import TableTask from './TableTask.vue'
import type { Block } from '@/types'

/*
 * One block of a page: the frontend half of the block "sandbox" (the backend's
 * AiSchemas defines what Claude may produce). A new block type needs a schema
 * there, a check in BlockValidator, and a component here.
 */
defineProps<{ block: Block }>()
const emit = defineEmits<{ solved: [] }>()
</script>

<template>
  <Markdown v-if="block.type === 'text'" :text="block.markdown" />
  <MultipleChoiceTask v-else-if="block.type === 'multiple_choice'" :block="block" @solved="emit('solved')" />
  <ClozeTask v-else-if="block.type === 'cloze'" :block="block" @solved="emit('solved')" />
  <TableTask v-else-if="block.type === 'table'" :block="block" @solved="emit('solved')" />
</template>
