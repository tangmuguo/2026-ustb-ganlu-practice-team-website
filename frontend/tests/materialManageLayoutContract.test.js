import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const materialManage = readFileSync(resolve(here, '../src/views/MaterialManage.vue'), 'utf8')

test('课件管理表格被限制在页面内容区，窄屏溢出只由表格区域承接', () => {
  const materialTableTag = materialManage.match(/<el-table[^>]*class="material-table"[^>]*>/)?.[0]

  assert.ok(materialTableTag)
  assert.ok(materialTableTag.includes('style="width: 100%"'))
  assert.equal(materialTableTag.match(/class="([^"]*)"/)?.[1].split(/\s+/).includes('table'), false)
  assert.match(materialManage, /\.manage-page\s*\{[^}]*width:\s*100%;[^}]*max-width:\s*1280px;[^}]*min-width:\s*0;/)
  assert.match(materialManage, /\.material-table-region\s*\{[^}]*width:\s*100%;[^}]*max-width:\s*100%;[^}]*min-width:\s*0;[^}]*overflow-x:\s*auto;/)
  assert.match(materialManage, /\.material-table\s*\{[^}]*width:\s*100%;[^}]*min-width:\s*980px;/)
})
