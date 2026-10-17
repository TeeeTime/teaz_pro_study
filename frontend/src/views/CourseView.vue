<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ProgressBar from '@/components/ProgressBar.vue'
import { api, errorMessage } from '@/api'
import type { CourseDetail, LectureSummary } from '@/types'

const props = defineProps<{ id: string }>()
const router = useRouter()

const ACCEPT = 'application/pdf,image/png,image/jpeg,image/gif,image/webp'
const POLL_MS = 3000

const course = ref<CourseDetail | null>(null)
const error = ref<string | null>(null)
const uploading = ref(false)
const dragging = ref(false)
const confirmDelete = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const progress = computed(() =>
  (course.value?.lectures ?? []).reduce(
    (sum, lecture) => ({
      completed: sum.completed + lecture.progress.completed,
      total: sum.total + lecture.progress.total,
    }),
    { completed: 0, total: 0 },
  ),
)
const busy = computed(() => course.value?.status === 'ANALYZING' || uploading.value)

let pollTimer: ReturnType<typeof setTimeout> | undefined

async function load() {
  clearTimeout(pollTimer)
  try {
    course.value = await api.get<CourseDetail>(`/api/courses/${props.id}`)
    error.value = null
  } catch (e) {
    error.value = errorMessage(e)
  }
  // The analysis runs in the background; check back until it's done.
  if (course.value?.status === 'ANALYZING') pollTimer = setTimeout(load, POLL_MS)
}

watch(() => props.id, load, { immediate: true })
onBeforeUnmount(() => clearTimeout(pollTimer))

async function upload(files: FileList | File[] | null) {
  if (!files || !files.length || busy.value) return
  const form = new FormData()
  for (const file of Array.from(files)) form.append('files', file)
  uploading.value = true
  error.value = null
  try {
    course.value = await api.post<CourseDetail>(`/api/courses/${props.id}/files`, form)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
    await load()
  }
}

function onDrop(event: DragEvent) {
  dragging.value = false
  upload(event.dataTransfer?.files ?? null)
}

async function retry() {
  try {
    course.value = await api.post<CourseDetail>(`/api/courses/${props.id}/analyze`)
  } catch (e) {
    error.value = errorMessage(e)
  }
  await load()
}

async function remove() {
  if (!confirmDelete.value) {
    confirmDelete.value = true
    setTimeout(() => (confirmDelete.value = false), 4000)
    return
  }
  try {
    await api.delete(`/api/courses/${props.id}`)
    router.push({ name: 'courses' })
  } catch (e) {
    error.value = errorMessage(e)
  }
}

function lectureState(lecture: LectureSummary): string | null {
  switch (lecture.pagesStatus) {
    case 'NOT_GENERATED':
      return 'Pages are created when you open it'
    case 'GENERATING':
      return 'Creating pages…'
    case 'FAILED':
      return 'Page creation failed, open to retry'
    default:
      return null
  }
}

function formatSize(bytes: number): string {
  return bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(bytes / 1024)} KB`
}
</script>

<template>
  <div class="view">
    <RouterLink :to="{ name: 'courses' }" class="crumb">Courses</RouterLink>

    <p v-if="error" class="notice notice--error" role="alert">{{ error }}</p>

    <template v-if="course">
      <header class="head">
        <h1 class="view__title">{{ course.title }}</h1>
        <ProgressBar v-if="course.lectures.length" :progress="progress" label="Course progress" class="head__progress" />
      </header>

      <p v-if="course.status === 'ANALYZING'" class="notice notice--working" role="status">
        Claude is reading the material and splitting it into lectures. This usually takes 1-5 minutes; you can leave
        this page meanwhile.
      </p>
      <div v-else-if="course.status === 'FAILED'" class="notice notice--error failed" role="alert">
        <span>{{ course.statusMessage ?? 'The analysis failed.' }}</span>
        <button type="button" class="button" @click="retry">Try again</button>
      </div>

      <section class="section">
        <h2 class="label">Lectures</h2>
        <p v-if="!course.lectures.length && course.status !== 'ANALYZING'" class="empty">
          Upload lecture slides, a script or photos of notes. Claude splits them into lectures; each lecture becomes
          clickable study pages with tasks.
        </p>
        <ol class="lectures">
          <li v-for="lecture in course.lectures" :key="lecture.id">
            <RouterLink :to="{ name: 'lecture', params: { id: lecture.id } }" class="lecture">
              <span class="lecture__number">{{ lecture.position + 1 }}</span>
              <span class="lecture__body">
                <span class="lecture__title">{{ lecture.title }}</span>
                <span class="lecture__summary">{{ lecture.summary }}</span>
                <span v-if="lectureState(lecture)" class="lecture__state">{{ lectureState(lecture) }}</span>
                <ProgressBar v-else :progress="lecture.progress" :label="`Progress in ${lecture.title}`" />
              </span>
            </RouterLink>
          </li>
        </ol>
      </section>

      <section class="section">
        <h2 class="label">{{ course.files.length ? 'Add material' : 'Upload material' }}</h2>
        <div
          class="drop"
          :class="{ 'drop--active': dragging, 'drop--disabled': busy }"
          @dragover.prevent="dragging = !busy"
          @dragleave="dragging = false"
          @drop.prevent="onDrop"
        >
          <p class="drop__text">
            {{ uploading ? 'Uploading…' : 'Drop PDFs or images here (PNG, JPEG, GIF, WebP).' }}
            <template v-if="course.files.length && !uploading">
              New material becomes new lectures after the existing ones.
            </template>
          </p>
          <button type="button" class="button" :disabled="busy" @click="fileInput?.click()">Choose files</button>
          <input
            ref="fileInput"
            type="file"
            :accept="ACCEPT"
            multiple
            hidden
            @change="upload(($event.target as HTMLInputElement).files)"
          />
        </div>

        <details v-if="course.files.length" class="files">
          <summary>{{ course.files.length }} {{ course.files.length === 1 ? 'file' : 'files' }} uploaded</summary>
          <ul>
            <li v-for="file in course.files" :key="file.id" class="file">
              <span class="file__name">{{ file.name }}</span>
              <span class="file__meta">
                {{ file.pageCount ? `${file.pageCount} pages · ` : '' }}{{ formatSize(file.sizeBytes) }}
                {{ file.analyzed ? '' : ' · not analyzed yet' }}
              </span>
            </li>
          </ul>
        </details>
      </section>

      <section class="section section--end">
        <button type="button" class="button" :class="{ 'button--primary': confirmDelete }" @click="remove">
          {{ confirmDelete ? 'Really delete course and all progress?' : 'Delete course' }}
        </button>
      </section>
    </template>
  </div>
</template>

<style scoped>
.crumb {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.crumb:hover {
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

.failed {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 32px;
}

.section--end {
  align-items: flex-start;
}

.empty {
  margin: 0;
  color: var(--color-text-secondary);
}

.lectures {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.lecture {
  display: flex;
  gap: 16px;
  padding: 14px 16px;
  color: inherit;
  text-decoration: none;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  transition: background-color var(--transition-fast);
}

.lecture:hover {
  background-color: var(--color-surface-hover);
}

.lecture:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.lecture__number {
  flex-shrink: 0;
  width: 2em;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.lecture__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.lecture__title {
  font-weight: 600;
  color: var(--color-text-primary);
}

.lecture__summary,
.lecture__state {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.lecture__state {
  font-style: italic;
}

.drop {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px;
  border: 1px dashed var(--color-border);
  transition:
    border-color var(--transition-fast),
    background-color var(--transition-fast);
}

.drop--active {
  border-color: var(--color-accent);
  background-color: var(--color-surface-hover);
}

.drop--disabled {
  opacity: 0.6;
}

.drop__text {
  margin: 0;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.files summary {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.files ul {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.file {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px 12px;
  padding: 6px 0;
  font-size: 0.85rem;
  border-top: 1px solid var(--color-border);
}

.file__name {
  overflow-wrap: anywhere;
}

.file__meta {
  color: var(--color-text-secondary);
}
</style>
