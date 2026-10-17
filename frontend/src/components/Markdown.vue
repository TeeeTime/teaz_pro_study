<script setup lang="ts">
import { computed } from 'vue'
import { renderInline, renderMarkdown } from '@/markdown'

const props = defineProps<{ text: string; inline?: boolean }>()

// Safe for v-html: markdown-it runs with html: false (see markdown.ts).
const html = computed(() => (props.inline ? renderInline(props.text) : renderMarkdown(props.text)))
</script>

<template>
  <span v-if="inline" class="markdown markdown--inline" v-html="html" />
  <div v-else class="markdown" v-html="html" />
</template>

<style scoped>
.markdown {
  min-width: 0;
  overflow-wrap: anywhere;
}

.markdown :deep(p),
.markdown :deep(ul),
.markdown :deep(ol),
.markdown :deep(pre),
.markdown :deep(blockquote),
.markdown :deep(table) {
  margin: 0 0 0.75em;
}

.markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.markdown :deep(ul),
.markdown :deep(ol) {
  padding-left: 1.4em;
}

.markdown :deep(code) {
  font-size: 0.9em;
  padding: 0 0.25em;
  background-color: var(--color-surface-hover);
}

.markdown :deep(pre) {
  padding: 12px;
  overflow-x: auto;
  background-color: var(--color-surface-hover);
}

.markdown :deep(blockquote) {
  padding-left: 12px;
  border-left: 3px solid var(--color-border);
  color: var(--color-text-secondary);
}

.markdown :deep(a) {
  color: var(--color-accent);
}

/* Display math scrolls on its own instead of widening the page on phones. */
.markdown :deep(.katex-display) {
  overflow-x: auto;
  overflow-y: hidden;
  padding: 4px 0;
}

.markdown :deep(strong) {
  font-weight: 600;
}
</style>
