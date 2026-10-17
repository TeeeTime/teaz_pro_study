<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProgressBar from '@/components/ProgressBar.vue'
import { api, errorMessage } from '@/api'
import type { CourseDetail, CourseSummary } from '@/types'

const router = useRouter()

const courses = ref<CourseSummary[] | null>(null)
const error = ref<string | null>(null)
const title = ref('')
const creating = ref(false)

onMounted(async () => {
  try {
    courses.value = await api.get<CourseSummary[]>('/api/courses')
  } catch (e) {
    error.value = errorMessage(e)
  }
})

async function create() {
  if (!title.value.trim()) return
  creating.value = true
  error.value = null
  try {
    const course = await api.post<CourseDetail>('/api/courses', { title: title.value.trim() })
    router.push({ name: 'course', params: { id: course.id } })
  } catch (e) {
    error.value = errorMessage(e)
    creating.value = false
  }
}

function statusText(course: CourseSummary): string | null {
  if (course.status === 'ANALYZING') return 'Analyzing material…'
  if (course.status === 'FAILED') return 'Analysis failed'
  if (course.lectureCount === 0) return 'No material yet'
  return null
}
</script>

<template>
  <div class="view">
    <h1 class="view__title">Courses</h1>

    <form class="create" @submit.prevent="create">
      <label class="create__field">
        <span class="label">New course</span>
        <input v-model="title" class="create__input" type="text" placeholder="e.g. Operations Research" maxlength="200" />
      </label>
      <button type="submit" class="button button--primary" :disabled="creating || !title.trim()">Create</button>
    </form>

    <p v-if="error" class="notice notice--error" role="alert">{{ error }}</p>

    <p v-if="courses && !courses.length" class="empty">
      No courses yet. Create one, then upload your lecture slides or script as PDF or images.
    </p>

    <ul v-if="courses?.length" class="courses">
      <li v-for="course in courses" :key="course.id">
        <RouterLink :to="{ name: 'course', params: { id: course.id } }" class="course">
          <span class="course__title">{{ course.title }}</span>
          <span class="course__meta">
            {{ course.lectureCount }} {{ course.lectureCount === 1 ? 'lecture' : 'lectures' }}
            <template v-if="statusText(course)"> · {{ statusText(course) }}</template>
          </span>
          <ProgressBar :progress="course.progress" :label="`Progress in ${course.title}`" />
        </RouterLink>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.create {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 8px;
  margin: 24px 0;
}

.create__field {
  display: flex;
  flex: 1 1 260px;
  flex-direction: column;
  gap: 4px;
  max-width: 480px;
}

.create__input {
  padding: 8px 10px;
  font: inherit;
  font-size: 0.9rem;
  color: var(--color-text-primary);
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.create__input:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 1px;
}

.empty {
  color: var(--color-text-secondary);
}

.courses {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  gap: 16px;
  padding: 0;
  list-style: none;
}

.course {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  padding: 16px;
  color: inherit;
  text-decoration: none;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: 0 1px 2px var(--color-shadow);
  transition:
    background-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.course:hover {
  background-color: var(--color-surface-hover);
  box-shadow: 0 4px 12px var(--color-shadow-strong);
}

.course:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.course__title {
  font-weight: 600;
  color: var(--color-text-primary);
}

.course__meta {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}
</style>
