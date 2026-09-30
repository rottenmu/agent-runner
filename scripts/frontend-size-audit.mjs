/* 前端代码规模审查：按 docs/rules/CODE_SIZE_RULES.md 的口径统计
 *   有效代码行 = 剔除 空行 / 纯注释行 / 单独的大括号行
 *   对 .vue 额外拆分 template / script / style 三段
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, extname } from 'node:path'

const ROOT = 'D:/codehub/agent_runner/frontend'
const SKIP = new Set(['node_modules', 'dist', '.git', 'logs'])
const EXTS = new Set(['.vue', '.js', '.ts', '.mjs'])

function walk(dir, out = []) {
  let names = []
  try { names = readdirSync(dir) } catch { return out }
  for (const name of names) {
    if (SKIP.has(name)) continue
    const p = join(dir, name)
    let st
    try { st = statSync(p) } catch { continue }
    if (st.isDirectory()) walk(p, out)
    else if (EXTS.has(extname(name)) && !name.includes('timestamp-')) out.push(p)
  }
  return out
}

const isComment = (l) => /^\s*(\/\/|\/\*|\*|\*\/|<!--|-->)/.test(l)
const isBrace = (l) => /^\s*[}\]\);,]+\s*$/.test(l)
const eff = (lines) => lines.filter((l) => l.trim() && !isComment(l) && !isBrace(l)).length

function splitVue(text) {
  const seg = {}
  for (const tag of ['template', 'script', 'style']) {
    const m = text.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`))
    seg[tag] = m ? m[1].split(/\r?\n/) : []
  }
  return seg
}

/** 粗略估算 script 内最长函数体（花括号配对；模板字符串里的 { } 会有误差，仅作线索） */
function longestFn(scriptLines) {
  const text = scriptLines.join('\n')
  const re = /(?:^|\n)(\s*)(?:async\s+)?(?:function\s+(\w+)|const\s+(\w+)\s*=\s*(?:async\s*)?\(|(\w+)\s*\([^)]*\)\s*\{)/g
  let best = { name: '-', len: 0, line: 0 }
  let m
  while ((m = re.exec(text)) !== null) {
    const name = m[2] || m[3] || m[4] || '(anonymous)'
    const startIdx = text.indexOf('{', m.index)
    if (startIdx < 0) continue
    let depth = 0, end = -1
    for (let i = startIdx; i < text.length; i++) {
      const c = text[i]
      if (c === '{') depth++
      else if (c === '}') { depth--; if (depth === 0) { end = i; break } }
    }
    if (end < 0) continue
    const body = text.slice(startIdx, end + 1)
    const lines = body.split('\n')
    const len = eff(lines)
    const lineNo = text.slice(0, m.index).split('\n').length
    if (len > best.len) best = { name, len, line: lineNo }
  }
  return best
}

const files = walk(ROOT)
const rows = []

for (const f of files) {
  const text = readFileSync(f, 'utf8')
  const all = text.split(/\r?\n/)
  const rel = relative(ROOT, f).replace(/\\/g, '/')
  const rec = { rel, total: all.length, eff: eff(all) }
  if (f.endsWith('.vue')) {
    const seg = splitVue(text)
    rec.template = eff(seg.template)
    rec.script = eff(seg.script)
    rec.style = seg.style.filter((l) => l.trim()).length
    const lf = longestFn(seg.script)
    rec.fn = `${lf.name}@${lf.line} (${lf.len})`
    rec.fnLen = lf.len
  } else {
    const lf = longestFn(all)
    rec.fn = `${lf.name}@${lf.line} (${lf.len})`
    rec.fnLen = lf.len
  }
  rows.push(rec)
}

const byProj = {}
for (const r of rows) {
  const proj = r.rel.split('/')[0]
  byProj[proj] = byProj[proj] || { n: 0, eff: 0, vue: 0 }
  byProj[proj].n++
  byProj[proj].eff += r.eff
  if (r.rel.endsWith('.vue')) byProj[proj].vue++
}

console.log('=== 各前端项目规模（有效代码行）===')
for (const [k, v] of Object.entries(byProj).sort((a, b) => b[1].eff - a[1].eff)) {
  console.log(`${String(v.eff).padStart(6)} 行  ${String(v.n).padStart(3)} 文件 (${v.vue} vue)  ${k}`)
}
console.log(`\n合计 ${rows.reduce((s, r) => s + r.eff, 0)} 有效行 / ${rows.length} 文件`)

const vueRows = rows.filter((r) => r.rel.endsWith('.vue')).sort((a, b) => b.eff - a.eff)
console.log('\n=== .vue 单文件有效行 Top 25（上限：页面500 / 组件300 / 弹窗200）===')
console.log('eff  script  tmpl  style  total  文件')
for (const r of vueRows.slice(0, 25)) {
  console.log(
    `${String(r.eff).padStart(4)} ${String(r.script).padStart(6)} ${String(r.template).padStart(5)} ` +
    `${String(r.style).padStart(6)} ${String(r.total).padStart(6)}  ${r.rel}`
  )
}

const jsRows = rows.filter((r) => !r.rel.endsWith('.vue')).sort((a, b) => b.eff - a.eff)
console.log('\n=== JS 单文件有效行 Top 20（上限：API 300 / utils 200 / hooks 150）===')
for (const r of jsRows.slice(0, 20)) {
  console.log(`${String(r.eff).padStart(4)}  ${r.rel}`)
}

console.log('\n=== 最长函数体 Top 15（上限 50 行）===')
for (const r of rows.filter((x) => x.fnLen).sort((a, b) => b.fnLen - a.fnLen).slice(0, 15)) {
  console.log(`${String(r.fnLen).padStart(4)} 行  ${r.rel}  → ${r.fn}`)
}
