import MarkdownIt from 'markdown-it'
import { katex } from '@mdit/plugin-katex'

/*
 * Markdown with LaTeX math ($...$ inline, $$...$$ display), rendered by KaTeX.
 * html: false means raw HTML in the (AI-written) text is escaped, never
 * rendered, so v-html on the output is safe.
 */
const md = new MarkdownIt({ html: false, linkify: true, typographer: true }).use(katex, {
  // A broken formula shows as red source instead of breaking the page.
  throwOnError: false,
})

export function renderMarkdown(text: string): string {
  return md.render(text)
}

/** For short pieces inside other markup (options, table cells): no wrapping <p>. */
export function renderInline(text: string): string {
  return md.renderInline(text)
}
