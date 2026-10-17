<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BlockView from '@/components/blocks/BlockView.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import { api, errorMessage } from '@/api'
import { isTask, type LectureDetail, type PageView } from '@/types'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()

const POLL_MS = 4000

const lecture = ref<LectureDetail | null>(null)
const error = ref<string | null>(null)
const finished = ref(false)
const chatOpen = ref(false)
/** Bumped on every reload, so task components start fresh when a page's content may have changed. */
const version = ref(0)
/** Indices of the solved task blocks on the current page. */
const solved = ref(new Set<number>())

const pages = computed(() => lecture.value?.pages ?? [])
const pageIndex = computed(() => {
  const requested = Number(route.query.page ?? NaN)
  if (Number.isInteger(requested) && requested >= 1 && requested <= pages.value.length) return requested - 1
  // Without ?page: continue where the learner left off.
  const firstOpen = pages.value.findIndex((page) => !page.completed)
  return firstOpen === -1 ? 0 : firstOpen
})
const page = computed<PageView | null>(() => pages.value[pageIndex.value] ?? null)
const taskIndices = computed(() =>
  (page.value?.blocks ?? []).flatMap((block, index) => (isTask(block) ? [index] : [])),
)
const progress = computed(() => ({
  completed: pages.value.filter((p) => p.completed).length,
  total: pages.value.length,
}))
const isLast = computed(() => pageIndex.value === pages.value.length - 1)

let pollTimer: ReturnType<typeof setTimeout> | undefined

async function load() {
  clearTimeout(pollTimer)
  try {
    lecture.value = await api.get<LectureDetail>(`/api/lectures/${props.id}`)
    version.value++
    error.value = null
    // Pages are made on first open: start it, then wait for it.
    if (lecture.value.pagesStatus === 'NOT_GENERATED') {
      lecture.value = await api.post<LectureDetail>(`/api/lectures/${props.id}/generate`)
    }
  } catch (e) {
    error.value = errorMessage(e)
  }
  if (lecture.value?.pagesStatus === 'GENERATING') pollTimer = setTimeout(load, POLL_MS)
  // Pin the resume page in the URL, so it stays put while pages get completed.
  if (lecture.value?.pagesStatus === 'READY' && route.query.page === undefined && pages.value.length) {
    router.replace({ query: { page: String(pageIndex.value + 1) } })
  }
}

async function retry() {
  try {
    lecture.value = await api.post<LectureDetail>(`/api/lectures/${props.id}/generate`)
  } catch (e) {
    error.value = errorMessage(e)
  }
  await load()
}

watch(
  () => props.id,
  () => {
    finished.value = false
    load()
  },
  { immediate: true },
)
watch(page, () => (solved.value = new Set()))
onBeforeUnmount(() => clearTimeout(pollTimer))

function goTo(index: number) {
  finished.value = false
  router.push({ query: { page: String(index + 1) } })
  window.scrollTo({ top: 0 })
}

async function markCompleted(target: PageView) {
  if (target.completed) return
  target.completed = true
  try {
    await api.put(`/api/pages/${target.id}/completed`, { completed: true })
  } catch (e) {
    target.completed = false
    error.value = errorMessage(e)
  }
}

/** Task pages count once every task is solved. */
function onSolved(blockIndex: number) {
  solved.value = new Set(solved.value).add(blockIndex)
  if (page.value && taskIndices.value.every((i) => solved.value.has(i))) markCompleted(page.value)
}

/** Info-only pages count when the learner moves on from them. */
function next() {
  // Read before marking: completing a page must not shift which page we're on.
  const current = pageIndex.value
  const last = isLast.value
  if (page.value && taskIndices.value.length === 0) markCompleted(page.value)
  if (last) {
    finished.value = true
    window.scrollTo({ top: 0 })
  } else {
    goTo(current + 1)
  }
}

async function resetProgress() {
  try {
    lecture.value = await api.post<LectureDetail>(`/api/lectures/${props.id}/reset-progress`)
    version.value++
    goTo(0)
  } catch (e) {
    error.value = errorMessage(e)
  }
}
</script>

<template>
  <div class="view lecture-view">
    <nav v-if="lecture" class="crumbs" aria-label="Breadcrumb">
      <RouterLink :to="{ name: 'courses' }">Courses</RouterLink>
      <span aria-hidden="true">/</span>
      <RouterLink :to="{ name: 'course', params: { id: lecture.courseId } }">{{ lecture.courseTitle }}</RouterLink>
    </nav>

    <p v-if="error" class="notice notice--error" role="alert">{{ error }}</p>

    <template v-if="lecture">
      <header class="head">
        <h1 class="view__title">{{ lecture.title }}</h1>
        <ProgressBar v-if="pages.length" :progress="progress" label="Lecture progress" class="head__progress" />
        <button type="button" class="button chat-toggle" :aria-expanded="chatOpen" @click="chatOpen = !chatOpen">
          {{ chatOpen ? 'Hide chat' : 'Chat' }}
        </button>
      </header>

      <div class="layout" :class="{ 'layout--chat-open': chatOpen }">
        <div class="main">
          <p v-if="lecture.pagesStatus === 'GENERATING'" class="notice notice--working" role="status">
            Claude is writing this lecture's study pages from the material. This takes about 1-3 minutes; you can leave
            this page and come back.
          </p>
          <div v-else-if="lecture.pagesStatus === 'FAILED'" class="notice notice--error failed" role="alert">
            <span>{{ lecture.statusMessage ?? 'Creating the pages failed.' }}</span>
            <button type="button" class="button" @click="retry">Try again</button>
          </div>

          <template v-if="lecture.pagesStatus === 'READY' && page">
            <ol class="pager" aria-label="Pages">
              <li v-for="(p, index) in pages" :key="p.id">
                <button
                  type="button"
                  class="pager__item"
                  :class="{ 'pager__item--current': index === pageIndex && !finished, 'pager__item--done': p.completed }"
                  :aria-current="index === pageIndex && !finished ? 'page' : undefined"
                  :aria-label="`Page ${index + 1}: ${p.title}${p.completed ? ' (done)' : ''}`"
                  :title="p.title"
                  @click="goTo(index)"
                >
                  {{ index + 1 }}
                </button>
              </li>
            </ol>

            <section v-if="finished" class="card done">
              <h2 class="card__title">Lecture done</h2>
              <p>
                {{ progress.completed }} of {{ progress.total }} pages completed.
                <template v-if="progress.completed < progress.total">
                  Open pages are not filled in the bar above; tasks count once solved.
                </template>
              </p>
              <div class="done__actions">
                <RouterLink
                  v-if="lecture.nextLectureId"
                  :to="{ name: 'lecture', params: { id: lecture.nextLectureId } }"
                  class="button button--primary"
                >
                  Next lecture
                </RouterLink>
                <RouterLink :to="{ name: 'course', params: { id: lecture.courseId } }" class="button">
                  Back to course
                </RouterLink>
                <button type="button" class="button" @click="resetProgress">Start over</button>
              </div>
            </section>

            <article v-else class="card page">
              <span class="label">Page {{ pageIndex + 1 }} of {{ pages.length }}<template v-if="page.completed"> · done</template></span>
              <h2 class="card__title">{{ page.title }}</h2>
              <BlockView
                v-for="(block, index) in page.blocks"
                :key="`${page.id}-${index}-${version}`"
                :block="block"
                @solved="onSolved(index)"
              />
              <footer class="page__nav">
                <button type="button" class="button" :disabled="pageIndex === 0" @click="goTo(pageIndex - 1)">
                  Previous
                </button>
                <span v-if="taskIndices.length && !page.completed" class="page__hint">
                  {{ solved.size }}/{{ taskIndices.length }} tasks solved
                </span>
                <button type="button" class="button button--primary" @click="next">
                  {{ isLast ? 'Finish lecture' : 'Next' }}
                </button>
              </footer>
            </article>
          </template>
        </div>

        <div class="side">
          <ChatPanel
            :key="lecture.id"
            :lecture-id="lecture.id"
            :enabled="lecture.pagesStatus === 'READY'"
            @applied="load"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.lecture-view {
  container-type: inline-size;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.crumbs a {
  color: inherit;
}

.crumbs a:hover {
  color: var(--color-text-primary);
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  margin: 8px 0 24px;
}

.head__progress {
  flex: 0 1 360px;
}

/* Wide: pages left, chat right (sticky). Narrow: chat above the pages, behind the header's toggle. */
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  align-items: start;
}

.main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.side {
  position: sticky;
  top: 72px;
  height: calc(100vh - 96px);
  min-height: 420px;
}

.chat-toggle {
  display: none;
}

@container (max-width: 960px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .side {
    display: none;
    order: -1;
    position: static;
    height: 70vh;
  }

  .layout--chat-open .side {
    display: block;
  }

  .chat-toggle {
    display: inline-flex;
  }
}

.failed {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
}

.pager {
  display: flex;
  gap: 4px;
  margin: 0;
  padding: 0 0 4px;
  overflow-x: auto;
  list-style: none;
}

.pager__item {
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  font: inherit;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  cursor: pointer;
}

.pager__item:hover {
  background-color: var(--color-surface-hover);
}

.pager__item:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 1px;
}

/* Done: filled bar at the bottom; current: accent outline. Shape, not only color. */
.pager__item--done {
  color: var(--color-text-primary);
  box-shadow: inset 0 -3px 0 var(--color-status-good);
}

.pager__item--current {
  font-weight: 700;
  color: var(--color-text-primary);
  border: 2px solid var(--color-accent);
}

.card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: 0 1px 2px var(--color-shadow);
}

.card__title {
  margin-top: -8px;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.page__nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.page__hint {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.done__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@container (max-width: 520px) {
  .card {
    padding: 16px;
  }
}
</style>
