import type { AnswerFields } from '@/types'

/** Compare text answers loosely: case, surrounding/duplicate spaces and LaTeX dollars don't matter. */
function normalize(text: string): string {
  return text.replace(/\$/g, '').trim().toLowerCase().replace(/\s+/g, ' ')
}

/**
 * "3,5", "3.5", " -2 ", "−2" (Unicode minus), "1/4" and "1e-3" all parse.
 * Returns null for anything else.
 */
export function parseNumber(input: string): number | null {
  const text = input.replace(/\s/g, '').replace(/−/g, '-').replace(',', '.')
  if (text === '') return null
  const fraction = /^(-?\d+(?:\.\d+)?)\/(-?\d+(?:\.\d+)?)$/.exec(text)
  if (fraction) {
    const denominator = Number(fraction[2])
    return denominator === 0 ? null : Number(fraction[1]) / denominator
  }
  const value = Number(text)
  return Number.isFinite(value) ? value : null
}

export function isCorrect(field: AnswerFields, input: string): boolean {
  if (field.numeric !== null) {
    const value = parseNumber(input)
    // Default tolerance only absorbs float noise; the AI sets a real one for rounded results.
    if (value !== null && Math.abs(value - field.numeric) <= (field.tolerance ?? 1e-9)) return true
  }
  const given = normalize(input)
  return given !== '' && field.answers.some((answer) => normalize(answer) === given)
}

/** What to show as the solution of a field. */
export function solution(field: AnswerFields): string {
  if (field.numeric !== null) return String(field.numeric).replace('.', ',')
  return field.answers[0] ?? ''
}
