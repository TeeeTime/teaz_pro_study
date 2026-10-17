<script setup lang="ts">
/*
 * One fill-in field (cloze gap or table cell). Marked wrong/right after a
 * check; once the solution is revealed it shows the expected answer next to it.
 * Wrong gets a thicker border all around, so it isn't told apart by color alone.
 */
defineProps<{ result: 'right' | 'wrong' | null; solution?: string | null; label: string }>()
const model = defineModel<string>({ required: true })
</script>

<template>
  <span class="answer">
    <input
      v-model="model"
      class="answer__input"
      :class="result && `answer__input--${result}`"
      type="text"
      inputmode="text"
      autocomplete="off"
      spellcheck="false"
      :aria-label="label"
      :aria-invalid="result === 'wrong' || undefined"
    />
    <span v-if="solution" class="answer__solution">= {{ solution }}</span>
  </span>
</template>

<style scoped>
.answer {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  vertical-align: baseline;
}

.answer__input {
  width: 7em;
  min-width: 4em;
  padding: 2px 6px;
  font: inherit;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);
  background-color: var(--color-bg);
  border: 1px solid var(--color-border);
  border-bottom-width: 2px;
  border-radius: var(--radius-sm);
}

.answer__input:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 1px;
}

.answer__input--right {
  border-bottom-color: var(--color-status-good);
}

.answer__input--wrong {
  border: 2px solid var(--color-status-critical);
}

.answer__solution {
  font-size: 0.85em;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
</style>
