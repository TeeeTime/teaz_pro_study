<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import Markdown from '@/components/Markdown.vue'
import { api, errorMessage } from '@/api'
import type { ChatMessage, Proposal } from '@/types'

const props = defineProps<{ lectureId: string; enabled: boolean }>()
/** A proposal was applied: the lecture's pages changed. */
const emit = defineEmits<{ applied: [] }>()

const messages = ref<ChatMessage[]>([])
const draft = ref('')
const sending = ref(false)
const busyId = ref<string | null>(null)
const error = ref<string | null>(null)
const listEl = ref<HTMLElement | null>(null)

const OP_LABEL: Record<Proposal['operations'][number]['op'], string> = {
  replace: 'Change',
  insert: 'Add',
  delete: 'Remove',
}

async function scrollToEnd() {
  await nextTick()
  listEl.value?.scrollTo({ top: listEl.value.scrollHeight })
}

onMounted(async () => {
  try {
    messages.value = await api.get<ChatMessage[]>(`/api/lectures/${props.lectureId}/chat`)
    await scrollToEnd()
  } catch (e) {
    error.value = errorMessage(e)
  }
})

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  sending.value = true
  error.value = null
  try {
    const turn = await api.post<ChatMessage[]>(`/api/lectures/${props.lectureId}/chat`, { text })
    messages.value.push(...turn)
    draft.value = ''
    await scrollToEnd()
  } catch (e) {
    // The question stays in the input to send again.
    error.value = errorMessage(e)
  } finally {
    sending.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

async function decide(message: ChatMessage, action: 'apply' | 'discard') {
  busyId.value = message.id
  error.value = null
  try {
    const updated = await api.post<ChatMessage>(`/api/chat/${message.id}/${action}`)
    Object.assign(message, updated)
    if (action === 'apply') emit('applied')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busyId.value = null
  }
}

function describe(operation: Proposal['operations'][number]): string {
  const page = operation.position + 1
  return operation.op === 'delete'
    ? `${OP_LABEL.delete} page ${page}`
    : `${OP_LABEL[operation.op]} page ${page}: ${operation.page?.title ?? ''}`
}
</script>

<template>
  <aside class="chat" aria-label="Lecture chat">
    <header class="chat__header">
      <span class="chat__title">Ask about this lecture</span>
      <span class="chat__hint">Questions, or changes like "more practice on …"</span>
    </header>

    <div ref="listEl" class="chat__messages">
      <p v-if="!messages.length && enabled" class="chat__empty">
        Ask anything about the material, or ask for changes to the lecture's pages.
      </p>
      <div
        v-for="message in messages"
        :key="message.id"
        class="chat__message"
        :class="`chat__message--${message.role.toLowerCase()}`"
      >
        <Markdown :text="message.text" />

        <div v-if="message.proposal" class="proposal">
          <span class="proposal__label">Proposed changes</span>
          <p class="proposal__summary">{{ message.proposal.summary }}</p>
          <ul class="proposal__ops">
            <li v-for="(operation, i) in message.proposal.operations" :key="i">{{ describe(operation) }}</li>
          </ul>
          <div v-if="message.proposalStatus === 'PENDING'" class="proposal__actions">
            <button
              type="button"
              class="button button--primary"
              :disabled="busyId === message.id"
              @click="decide(message, 'apply')"
            >
              Apply
            </button>
            <button type="button" class="button" :disabled="busyId === message.id" @click="decide(message, 'discard')">
              Discard
            </button>
          </div>
          <span v-else class="proposal__status">{{ message.proposalStatus === 'APPLIED' ? 'Applied' : 'Discarded' }}</span>
        </div>
      </div>
      <p v-if="sending" class="chat__pending" role="status">Thinking…</p>
    </div>

    <p v-if="error" class="chat__error" role="alert">{{ error }}</p>

    <form class="chat__form" @submit.prevent="send">
      <textarea
        v-model="draft"
        class="chat__input"
        rows="3"
        :disabled="!enabled || sending"
        :placeholder="enabled ? 'Message (Enter to send, Shift+Enter for a new line)' : 'Available once the pages exist'"
        aria-label="Message"
        @keydown="onKeydown"
      />
      <button type="submit" class="button button--primary" :disabled="!enabled || sending || !draft.trim()">Send</button>
    </form>
  </aside>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
}

.chat__header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.chat__title {
  font-weight: 600;
  color: var(--color-text-primary);
}

.chat__hint,
.chat__empty,
.chat__pending {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.chat__messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.chat__message {
  max-width: 100%;
  padding: 10px 12px;
  font-size: 0.9rem;
  border: 1px solid var(--color-border);
}

.chat__message--user {
  align-self: flex-end;
  max-width: 90%;
  background-color: var(--color-surface-hover);
}

.chat__message--assistant {
  background-color: var(--color-bg);
}

.proposal {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-accent);
}

.proposal__label,
.proposal__status {
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.proposal__summary {
  margin: 0;
}

.proposal__ops {
  margin: 0;
  padding-left: 1.2em;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.proposal__actions {
  display: flex;
  gap: 8px;
}

.chat__error {
  margin: 0;
  padding: 8px 16px;
  font-size: 0.85rem;
  border-top: 1px solid var(--color-border);
  border-left: 3px solid var(--color-status-critical);
}

.chat__form {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 12px;
  border-top: 1px solid var(--color-border);
}

.chat__input {
  flex: 1;
  min-width: 0;
  resize: vertical;
  padding: 8px 10px;
  font: inherit;
  font-size: 0.9rem;
  color: var(--color-text-primary);
  background-color: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.chat__input:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 1px;
}
</style>
