import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { siteContent } from '../src/config/siteContent.js'

const here = dirname(fileURLToPath(import.meta.url))
const aboutPage = readFileSync(resolve(here, '../src/views/AboutGanlu.vue'), 'utf8')

test('关于甘露使用已确认的简介、服务地区和发展历程', () => {
  assert.equal(siteContent.about.introduction, '甘露支教是一支以教育志愿服务为核心的实践团队。')
  assert.deepEqual(siteContent.about.serviceRegions, ['甘肃', '四川', '贵州', '福建', '广东', '河南', '湖南', '内蒙古', '…'])
  assert.deepEqual(siteContent.about.timeline, [
    {
      label: '团队成立',
      description: '8年坚守，甘露685名成员走进64所乡村学校，陪伴3410名孩子成长，我们整合8年实践经验，提炼出“教育帮扶+科技赋能+调研实践”的模式，构建了可持续的教育帮扶体系。',
    },
    {
      label: '整体计划',
      description: '团队将持续深耕“互联网+教育”创新模式，迭代升级甘露云课堂线上助学平台，联动实地支教与乡村教育调研。同时深耕红色爱国教育、科技素质培育两大特色课堂，以多元课程内容，为乡村孩童带去更丰富、更贴合需求的教育陪伴。',
    },
  ])
})

test('关于甘露页面渲染新的理念和时间线标签', () => {
  assert.match(aboutPage, /以钢铁之魂铸求知火种，以山川为卷写青春作答/)
  assert.match(aboutPage, /item\.label/)
  assert.doesNotMatch(aboutPage, /item\.(year|title)/)
  assert.doesNotMatch(aboutPage, /我们如何走到今天/)
  assert.doesNotMatch(aboutPage, /我们去往哪里/)
  assert.match(aboutPage, /section-kicker-large/)
  assert.match(aboutPage, /\.section-kicker-large \{ display: block; font-size: 35px;/)
  assert.match(aboutPage, /\.philosophy-card h2 \{ font-size: 25px; \}/)
  assert.match(aboutPage, /\.service-region-list \{ margin-top: 68px; \}/)
})
