/** API shapes, mirroring the backend's *Dtos records. */

export interface Progress {
  completed: number
  total: number
}

export type CourseStatus = 'READY' | 'ANALYZING' | 'FAILED'
export type PagesStatus = 'NOT_GENERATED' | 'GENERATING' | 'READY' | 'FAILED'

export interface CourseSummary {
  id: string
  title: string
  status: CourseStatus
  statusMessage: string | null
  lectureCount: number
  progress: Progress
}

export interface FileView {
  id: string
  name: string
  mediaType: string
  sizeBytes: number
  pageCount: number | null
  analyzed: boolean
}

export interface LectureSummary {
  id: string
  position: number
  title: string
  summary: string
  pagesStatus: PagesStatus
  statusMessage: string | null
  progress: Progress
}

export interface CourseDetail {
  id: string
  title: string
  status: CourseStatus
  statusMessage: string | null
  files: FileView[]
  lectures: LectureSummary[]
}

export interface PageView {
  id: string
  position: number
  title: string
  blocks: Block[]
  completed: boolean
}

export interface LectureDetail {
  id: string
  courseId: string
  courseTitle: string
  title: string
  summary: string
  pagesStatus: PagesStatus
  statusMessage: string | null
  previousLectureId: string | null
  nextLectureId: string | null
  pages: PageView[]
}

export interface Proposal {
  summary: string
  operations: { op: 'replace' | 'insert' | 'delete'; position: number; page: { title: string; blocks: Block[] } | null }[]
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  text: string
  proposal: Proposal | null
  proposalStatus: 'PENDING' | 'APPLIED' | 'DISCARDED' | null
  createdAt: string
}

// Blocks: the "sandbox" of page content, see the backend's AiSchemas.

/** What a learner's input is compared against: accepted strings and/or a number with tolerance. */
export interface AnswerFields {
  answers: string[]
  numeric: number | null
  tolerance: number | null
}

export interface TextBlock {
  type: 'text'
  markdown: string
}

export interface MultipleChoiceBlock {
  type: 'multiple_choice'
  question: string
  options: string[]
  correct: number[]
  explanation: string
}

export interface ClozeBlock {
  type: 'cloze'
  instruction: string
  /** Markdown with gaps {{1}}, {{2}}, ... */
  text: string
  gaps: AnswerFields[]
  explanation: string
}

export interface TableCell extends AnswerFields {
  /** Fixed content; null = the learner fills it in. */
  given: string | null
}

export interface TableBlock {
  type: 'table'
  instruction: string
  columns: string[]
  rows: TableCell[][]
  explanation: string
}

export type Block = TextBlock | MultipleChoiceBlock | ClozeBlock | TableBlock

export function isTask(block: Block): boolean {
  return block.type !== 'text'
}
