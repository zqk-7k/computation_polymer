<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTopbar from '../components/AppTopbar.vue'
import { fetchDatasetCatalog } from '../api'

const route = useRoute()
const router = useRouter()
const catalog = ref([])
const loading = ref(true)
const error = ref('')
const search = ref('')
const dataType = ref('')
const calculationMethod = ref('')
const functional = ref('')
const basisSet = ref('')
const software = ref('')
const atomMin = ref('')
const atomMax = ref('')
const selectedProperties = ref([])
const selectedElements = ref([])
let syncing = false

const datasetMeta = {
  ani_gdb_s03: { code: 'ANI', accent: '#3f7edb' },
  data0000_aselmdb: { code: 'ASE', accent: '#20a394' },
  openpoly_calculated: { code: 'OP', accent: '#43a867' },
  ani1x_less_is_more: { code: '1x', accent: '#7a68d8' },
  transition1x: { code: 'TS', accent: '#df6a62' },
  twod_matpedia: { code: '2D', accent: '#c9992e' },
  jarvis_dft_3d: { code: '3D', accent: '#248a99' },
  jarvis_dft_2d: { code: 'JV', accent: '#956bd6' },
  polymer_genome_1073: { code: 'PG', accent: '#5aa873' },
  qmof_database: { code: 'MOF', accent: '#2ca7a1' },
  matbench_wbm_summary: { code: 'WBM', accent: '#4f8f7b' },
  matbench_mp_energies: { code: 'MP', accent: '#6478b8' },
  matbench_phonondb_pbe_103: { code: 'Ph', accent: '#b1843f' },
  hydrocarbons_gap_ch: { code: 'CH', accent: '#6f8f4f' },
  matbench_v01_dielectric: { code: 'Di', accent: '#4b8f86' },
  matbench_v01_jdft2d: { code: 'J2', accent: '#b28a3c' },
  matbench_v01_phonons: { code: 'Pn', accent: '#7b8fbf' },
  matbench_v01_perovskites: { code: 'Pv', accent: '#6f9a5f' },
  matbench_v01_log_gvrh: { code: 'G', accent: '#9b725f' },
  matbench_v01_log_kvrh: { code: 'K', accent: '#8b7bb8' },
  qm9_molecular_dft: { code: 'Q9', accent: '#5b8fb0' }
}

const propertyPriority = [
  '三维坐标', '能量', '总能量', '每原子能量', '原子化能', '力', 'Band gap', 'HOMO/LUMO',
  'HOMO-LUMO gap', '介电常数', '形成能', '稳定性/凸包能', '剥离能', '分解能',
  '热力学校正', '反应路径', '磁性', '晶胞/空间群'
]
const elementPriority = ['H', 'C', 'N', 'O', 'F', 'S', 'Cl', 'B', 'Si', 'P', 'Cu', 'Zn', 'Cd', 'Ag', 'I']

onMounted(async () => {
  hydrateFromQuery()
  try {
    catalog.value = await fetchDatasetCatalog()
  } catch (err) {
    error.value = err.message || '数据目录加载失败'
  } finally {
    loading.value = false
  }
})

watch(
  [search, dataType, calculationMethod, functional, basisSet, software, atomMin, atomMax, selectedProperties, selectedElements],
  () => syncQuery(),
  { deep: true }
)

function hydrateFromQuery() {
  syncing = true
  search.value = String(route.query.q || '')
  dataType.value = String(route.query.type || '')
  calculationMethod.value = String(route.query.method || '')
  functional.value = String(route.query.functional || '')
  basisSet.value = String(route.query.basis || '')
  software.value = String(route.query.software || '')
  atomMin.value = String(route.query.atomMin || '')
  atomMax.value = String(route.query.atomMax || '')
  selectedProperties.value = listFromQuery(route.query.properties)
  selectedElements.value = listFromQuery(route.query.elements)
  syncing = false
}

function listFromQuery(value) {
  return value ? String(value).split(',').filter(Boolean) : []
}

function syncQuery() {
  if (syncing) return
  const query = {}
  if (search.value) query.q = search.value
  if (dataType.value) query.type = dataType.value
  if (calculationMethod.value) query.method = calculationMethod.value
  if (functional.value) query.functional = functional.value
  if (basisSet.value) query.basis = basisSet.value
  if (software.value) query.software = software.value
  if (atomMin.value !== '') query.atomMin = atomMin.value
  if (atomMax.value !== '') query.atomMax = atomMax.value
  if (selectedProperties.value.length) query.properties = selectedProperties.value.join(',')
  if (selectedElements.value.length) query.elements = selectedElements.value.join(',')
  router.replace({ name: 'explore', query })
}

function uniqueOptions(key) {
  return computed(() => Array.from(new Set(catalog.value.flatMap(item => item[key] || []))).sort((a, b) => a.localeCompare(b, 'zh-CN')))
}

const dataTypes = computed(() => Array.from(new Set(catalog.value.map(item => item.dataType))).sort((a, b) => a.localeCompare(b, 'zh-CN')))
const calculationMethods = uniqueOptions('calculationMethods')
const functionals = uniqueOptions('functionals')
const basisSets = uniqueOptions('basisSets')
const softwareOptions = uniqueOptions('software')
const properties = computed(() => sortByPriority(Array.from(new Set(catalog.value.flatMap(item => item.properties))), propertyPriority))
const elements = computed(() => sortByPriority(Array.from(new Set(catalog.value.flatMap(item => item.elements))), elementPriority))

function sortByPriority(values, priority) {
  return values.sort((a, b) => {
    const ai = priority.indexOf(a)
    const bi = priority.indexOf(b)
    if (ai >= 0 || bi >= 0) return (ai < 0 ? 999 : ai) - (bi < 0 ? 999 : bi)
    return a.localeCompare(b, 'zh-CN')
  })
}

const results = computed(() => {
  const text = search.value.trim().toLowerCase()
  const min = atomMin.value === '' ? null : Number(atomMin.value)
  const max = atomMax.value === '' ? null : Number(atomMax.value)
  return catalog.value
    .filter(item => {
      const haystack = [
        item.id, item.name, item.intro, item.dataType, item.representation,
        ...(item.calculationMethods || []), ...(item.functionals || []), ...(item.basisSets || []),
        ...(item.software || []), ...(item.properties || []), ...(item.elements || []),
        ...(item.links || []).map(link => `${link.label} ${link.url}`)
      ].join(' ').toLowerCase()
      if (text && !haystack.includes(text)) return false
      if (dataType.value && item.dataType !== dataType.value) return false
      if (calculationMethod.value && !item.calculationMethods.includes(calculationMethod.value)) return false
      if (functional.value && !item.functionals.includes(functional.value)) return false
      if (basisSet.value && !item.basisSets.includes(basisSet.value)) return false
      if (software.value && !item.software.includes(software.value)) return false
      if (Number.isFinite(min) && item.maxAtoms < min) return false
      if (Number.isFinite(max) && item.minAtoms > max) return false
      if (!selectedProperties.value.every(property => item.properties.includes(property))) return false
      if (!selectedElements.value.every(element => item.elements.includes(element))) return false
      return true
    })
    .sort((a, b) => {
      if (text) {
        const aScore = String(a.name).toLowerCase().includes(text) ? 1 : 0
        const bScore = String(b.name).toLowerCase().includes(text) ? 1 : 0
        if (aScore !== bScore) return bScore - aScore
      }
      return b.displayRecords - a.displayRecords
    })
})

const activeCount = computed(() => [
  search.value, dataType.value, calculationMethod.value, functional.value, basisSet.value, software.value,
  atomMin.value, atomMax.value, ...selectedProperties.value, ...selectedElements.value
].filter(Boolean).length)

function metaFor(id) {
  return datasetMeta[id] || { code: 'DFT', accent: '#2f6fed' }
}

function toggleElement(value) {
  selectedElements.value = selectedElements.value.includes(value)
    ? selectedElements.value.filter(item => item !== value)
    : [...selectedElements.value, value]
}

function toggleProperty(value) {
  selectedProperties.value = selectedProperties.value.includes(value)
    ? selectedProperties.value.filter(item => item !== value)
    : [...selectedProperties.value, value]
}

function clearFilters() {
  search.value = ''
  dataType.value = ''
  calculationMethod.value = ''
  functional.value = ''
  basisSet.value = ''
  software.value = ''
  atomMin.value = ''
  atomMax.value = ''
  selectedProperties.value = []
  selectedElements.value = []
}

function openDataset(item) {
  const query = {}
  if (atomMin.value !== '') query.atomMin = atomMin.value
  if (atomMax.value !== '') query.atomMax = atomMax.value
  router.push({ name: 'dataset-records', params: { id: item.id }, query })
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function backHome() {
  openHomeTab('data')
}

function openHomeTab(tab) {
  router.push({
    name: 'home',
    query: tab === 'data' ? {} : { tab }
  })
}

function openAssistant() {
  router.push({ name: 'assistant' })
}
</script>

<template>
  <div class="page">
    <AppTopbar show-back @brand-click="backHome">
      <nav class="main-nav">
        <button @click="backHome">数据中心</button>
        <button class="active">数据发现</button>
        <button @click="openHomeTab('quality')">质量验证</button>
        <button @click="openAssistant">智能助手</button>
        <button @click="openHomeTab('model')">模型</button>
        <button @click="openHomeTab('workflow')">工作流</button>
      </nav>
    </AppTopbar>

    <main class="shell">
      <section class="header">
        <div>
          <p class="eyebrow">Dataset Explorer</p>
          <h1>数据发现</h1>
          <p class="intro">从结构类型、元素组成、计算设置和可用性质中定位适合研究任务的数据集。</p>
        </div>
        <div class="header-metrics">
          <strong>{{ results.length }}</strong>
          <span>匹配数据集 / {{ catalog.length }}</span>
        </div>
      </section>

      <label class="global-search">
        <span>全局检索</span>
        <input v-model="search" type="search" placeholder="数据集名称、DOI、VASP、ωB97X、Band gap...">
      </label>

      <div class="layout">
        <aside class="filters">
          <div class="filter-heading">
            <h2>筛选条件</h2>
            <button v-if="activeCount" @click="clearFilters">清空</button>
          </div>

          <label>
            <span>研究对象</span>
            <select v-model="dataType">
              <option value="">全部类型</option>
              <option v-for="value in dataTypes" :key="value" :value="value">{{ value }}</option>
            </select>
          </label>
          <label>
            <span>计算方法</span>
            <select v-model="calculationMethod">
              <option value="">全部方法</option>
              <option v-for="value in calculationMethods" :key="value" :value="value">{{ value }}</option>
            </select>
          </label>
          <label>
            <span>交换-相关泛函</span>
            <select v-model="functional">
              <option value="">全部泛函</option>
              <option v-for="value in functionals" :key="value" :value="value">{{ value }}</option>
            </select>
          </label>
          <label>
            <span>基组 / 赝势设置</span>
            <select v-model="basisSet">
              <option value="">全部设置</option>
              <option v-for="value in basisSets" :key="value" :value="value">{{ value }}</option>
            </select>
          </label>
          <label>
            <span>计算软件</span>
            <select v-model="software">
              <option value="">全部软件</option>
              <option v-for="value in softwareOptions" :key="value" :value="value">{{ value }}</option>
            </select>
          </label>

          <fieldset>
            <legend>原子数范围</legend>
            <div class="range">
              <input v-model="atomMin" type="number" min="0" placeholder="最小">
              <span>-</span>
              <input v-model="atomMax" type="number" min="0" placeholder="最大">
            </div>
          </fieldset>

          <fieldset>
            <legend>包含元素</legend>
            <div class="chips elements">
              <button
                v-for="element in elements"
                :key="element"
                :class="{ selected: selectedElements.includes(element) }"
                @click="toggleElement(element)"
              >{{ element }}</button>
            </div>
          </fieldset>

          <fieldset>
            <legend>需要的性质字段</legend>
            <div class="chips properties">
              <button
                v-for="property in properties"
                :key="property"
                :class="{ selected: selectedProperties.includes(property) }"
                @click="toggleProperty(property)"
              >{{ property }}</button>
            </div>
          </fieldset>
        </aside>

        <section class="results">
          <div class="results-head">
            <h2>匹配结果</h2>
            <span v-if="activeCount">{{ activeCount }} 项筛选已启用</span>
          </div>
          <p v-if="loading" class="state">正在加载数据目录...</p>
          <p v-else-if="error" class="state error">{{ error }}</p>
          <p v-else-if="!results.length" class="state">没有满足当前组合条件的数据集。</p>

          <template v-else>
            <article
              v-for="item in results"
              :key="item.id"
              class="result-card"
              :style="{ '--accent': metaFor(item.id).accent }"
            >
              <div class="card-heading">
                <span class="code">{{ metaFor(item.id).code }}</span>
                <div>
                  <h3>{{ item.name }}</h3>
                  <p>{{ item.dataType }} · {{ item.id }}</p>
                </div>
                <button class="open" @click="openDataset(item)">进入数据集</button>
              </div>
              <p class="description">{{ item.intro }}</p>
              <div class="columns">
                <div class="metadata">
                  <span>展示记录 <strong>{{ formatNumber(item.displayRecords) }}</strong></span>
                  <span>原子数 <strong>{{ item.minAtoms }}-{{ item.maxAtoms }}</strong></span>
                  <span>泛函 <strong>{{ item.functionals.join(' / ') }}</strong></span>
                  <span>基组/赝势 <strong>{{ item.basisSets.join(' / ') }}</strong></span>
                  <span v-if="item.software.length">软件 <strong>{{ item.software.join(' / ') }}</strong></span>
                  <span>展示策略 <strong>{{ item.representation }}</strong></span>
                </div>
                <div>
                  <p class="tag-label">可用性质</p>
                  <div class="property-tags">
                    <span
                      v-for="property in item.properties"
                      :key="property"
                      :class="{ matched: selectedProperties.includes(property) }"
                    >{{ property }}</span>
                  </div>
                  <p class="tag-label">元素</p>
                  <div class="element-tags">
                    <span
                      v-for="element in item.elements"
                      :key="element"
                      :class="{ matched: selectedElements.includes(element) }"
                    >{{ element }}</span>
                  </div>
                </div>
              </div>
            </article>
          </template>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

button,
input,
select {
  font: inherit;
}

.shell {
  width: min(1480px, calc(100% - 48px));
  margin: 0 auto;
  padding: 30px 0 50px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 28px;
}

.eyebrow {
  margin: 0 0 7px;
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.header h1 {
  margin: 0 0 9px;
  font-size: 34px;
}

.intro {
  margin: 0;
  color: var(--vs-text-secondary);
}

.header-metrics {
  min-width: 164px;
  padding: 14px 18px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
}

.header-metrics strong {
  display: block;
  color: var(--vs-primary);
  font-size: 29px;
}

.header-metrics span {
  color: var(--vs-text-secondary);
  font-size: 13px;
}

.global-search {
  display: grid;
  gap: 8px;
  margin-top: 26px;
  color: var(--vs-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.global-search input {
  height: 54px;
  padding: 0 18px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  color: var(--vs-text);
  font-size: 15px;
}

.layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 20px;
  margin-top: 18px;
  align-items: start;
}

.filters {
  position: sticky;
  top: 78px;
  display: grid;
  gap: 17px;
  padding: 18px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
}

.filter-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-heading h2,
.results-head h2 {
  margin: 0;
  font-size: 18px;
}

.filter-heading button {
  border: 0;
  background: transparent;
  color: var(--vs-primary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
}

.filters label {
  display: grid;
  gap: 7px;
  color: var(--vs-text-secondary);
  font-size: 12px;
  font-weight: 800;
}

select,
.range input {
  width: 100%;
  height: 40px;
  padding: 0 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text);
}

fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}

legend {
  margin-bottom: 9px;
  color: var(--vs-text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.range {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 7px;
  align-items: center;
}

.range span {
  color: var(--vs-text-tertiary);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chips button {
  min-height: 29px;
  padding: 0 9px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  cursor: pointer;
  font-size: 12px;
}

.chips button.selected {
  border-color: var(--vs-primary);
  background: color-mix(in srgb, var(--vs-primary) 12%, var(--vs-card));
  color: var(--vs-primary);
  font-weight: 800;
}

.results {
  display: grid;
  gap: 12px;
}

.results-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 2px 6px;
}

.results-head span {
  color: var(--vs-primary);
  font-size: 13px;
  font-weight: 800;
}

.state,
.result-card {
  margin: 0;
  padding: 18px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
}

.state {
  color: var(--vs-text-secondary);
}

.state.error {
  color: #c84d58;
}

.result-card {
  border-left: 3px solid var(--accent);
}

.card-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code {
  width: 46px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--accent) 13%, var(--vs-card));
  color: var(--accent);
  font-size: 12px;
  font-weight: 900;
}

.card-heading h3 {
  margin: 0 0 4px;
  font-size: 17px;
}

.card-heading p {
  margin: 0;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.open {
  height: 36px;
  margin-left: auto;
  padding: 0 13px;
  border: 1px solid var(--accent);
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--accent) 10%, var(--vs-card));
  color: var(--accent);
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
}

.description {
  margin: 14px 0 15px;
  color: var(--vs-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.columns {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) 1.1fr;
  gap: 18px;
}

.metadata {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px 13px;
}

.metadata span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.metadata strong {
  display: block;
  margin-top: 3px;
  color: var(--vs-text);
  font-size: 13px;
  font-weight: 700;
}

.tag-label {
  margin: 0 0 6px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.tag-label:not(:first-child) {
  margin-top: 10px;
}

.property-tags,
.element-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.property-tags span,
.element-tags span {
  padding: 4px 7px;
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.property-tags span.matched,
.element-tags span.matched {
  background: color-mix(in srgb, var(--accent) 13%, var(--vs-card));
  color: var(--accent);
  font-weight: 800;
}

@media (max-width: 960px) {
  .shell {
    width: min(100% - 32px, 700px);
  }

  .header {
    align-items: stretch;
    flex-direction: column;
  }

  .layout {
    grid-template-columns: 1fr;
  }

  .filters {
    position: static;
  }

  .columns {
    grid-template-columns: 1fr;
  }
}
</style>
