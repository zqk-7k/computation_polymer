<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadDataset, fetchDatasetDetail, fetchDatasetRecords, fetchDatasetStats } from '../api'
import { authUser, canDownloadDataset } from '../auth/session'
import AppTopbar from '../components/AppTopbar.vue'
import DatasetStatsCharts from '../components/DatasetStatsCharts.vue'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const stats = ref(null)
const records = ref([])
const total = ref(0)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const search = ref('')
const energyMin = ref('')
const energyMax = ref('')
const atomMin = ref('')
const atomMax = ref('')
const appliedFilters = ref({
  search: '',
  energyMin: '',
  energyMax: '',
  atomMin: '',
  atomMax: ''
})
const offset = ref(0)
const pageInput = ref('1')
const limit = 24

const datasetMeta = {
  ani_gdb_s03: { code: 'ANI', kind: '小分子构象', accent: '#3f7edb' },
  data0000_aselmdb: { code: 'ASE', kind: '聚合物量化', accent: '#20a394' },
  openpoly_calculated: { code: 'OP', kind: '聚合物性质', accent: '#43a867' },
  ani1x_less_is_more: { code: '1x', kind: '主动学习', accent: '#7a68d8' },
  transition1x: { code: 'TS', kind: '反应路径', accent: '#df6a62' },
  twod_matpedia: { code: '2D', kind: '二维材料', accent: '#c9992e' },
  jarvis_dft_3d: { code: '3D', kind: '晶体材料', accent: '#248a99' },
  jarvis_dft_2d: { code: 'JV', kind: '二维材料', accent: '#956bd6' },
  polymer_genome_1073: { code: 'PG', kind: '聚合物结构', accent: '#5aa873' },
  qmof_database: { code: 'MOF', kind: '框架材料', accent: '#2ca7a1' },
  matbench_wbm_summary: { code: 'WBM', kind: '稳定性基准', accent: '#4f8f7b' },
  matbench_mp_energies: { code: 'MP', kind: '参考能量', accent: '#6478b8' },
  matbench_phonondb_pbe_103: { code: 'Ph', kind: '热输运', accent: '#b1843f' },
  hydrocarbons_gap_ch: { code: 'CH', kind: '反应势训练', accent: '#6f8f4f' }
}

const page = computed(() => Math.floor(offset.value / limit) + 1)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / limit)))
const isAniDataset = computed(() => route.params.id === 'ani_gdb_s03')
const currentMeta = computed(() => datasetMeta[route.params.id] || { code: 'DFT', kind: '计算数据', accent: '#2f6fed' })
const elementList = computed(() => detail.value?.elements || [])
const methodParts = computed(() => [detail.value?.method, detail.value?.scale].filter(Boolean))
const activeFilterCount = computed(() => Object.values(appliedFilters.value).filter(value => value !== '').length)
const pageRange = computed(() => {
  if (!total.value) return '0-0'
  const start = offset.value + 1
  const end = Math.min(offset.value + limit, total.value)
  return `${start.toLocaleString('zh-CN')}-${end.toLocaleString('zh-CN')}`
})
const recordUnit = computed(() => {
  if (route.params.id === 'ani_gdb_s03') return '构象记录'
  if (route.params.id === 'ani1x_less_is_more') return '分子构型组'
  if (route.params.id === 'transition1x') return '反应路径'
  if (route.params.id === 'qmof_database') return 'MOF 结构'
  if (route.params.id === 'hydrocarbons_gap_ch') return 'C/H 构型'
  if (route.params.id === 'matbench_wbm_summary') return 'WBM 材料'
  if (route.params.id === 'matbench_mp_energies') return 'MP 条目'
  if (route.params.id === 'matbench_phonondb_pbe_103') return '热输运结构'
  if (['twod_matpedia', 'jarvis_dft_3d', 'jarvis_dft_2d'].includes(route.params.id)) return '材料记录'
  return '聚合物记录'
})
const energyFieldLabel = computed(() => ({
  twod_matpedia: '每原子能量',
  polymer_genome_1073: '原子化能',
  qmof_database: 'PBE 总能量',
  matbench_wbm_summary: '未校正能量',
  matbench_mp_energies: '每原子能量',
  matbench_phonondb_pbe_103: '平均热导率',
  hydrocarbons_gap_ch: 'DFT 能量'
}[route.params.id] || '总能量'))

onMounted(() => {
  hydrateFromQuery()
  loadAll()
})

watch(page, value => {
  pageInput.value = String(value)
})

function hydrateFromQuery() {
  search.value = route.query.search || ''
  energyMin.value = route.query.energyMin || ''
  energyMax.value = route.query.energyMax || ''
  atomMin.value = route.query.atomMin || ''
  atomMax.value = route.query.atomMax || ''
  applyDraftFilters()
  const queryPage = Number(route.query.page || 1)
  offset.value = Number.isFinite(queryPage) && queryPage > 0 ? (queryPage - 1) * limit : 0
  pageInput.value = String(Math.floor(offset.value / limit) + 1)
}

function currentQuery() {
  const query = {}
  if (appliedFilters.value.search) query.search = appliedFilters.value.search
  if (appliedFilters.value.energyMin !== '') query.energyMin = appliedFilters.value.energyMin
  if (appliedFilters.value.energyMax !== '') query.energyMax = appliedFilters.value.energyMax
  if (appliedFilters.value.atomMin !== '') query.atomMin = appliedFilters.value.atomMin
  if (appliedFilters.value.atomMax !== '') query.atomMax = appliedFilters.value.atomMax
  if (page.value > 1) query.page = String(page.value)
  return query
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await fetchDatasetDetail(route.params.id)
    stats.value = await fetchDatasetStats(route.params.id)
    await loadRecords()
  } catch (err) {
    error.value = err.message || '数据集加载失败'
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  const data = await fetchDatasetRecords(route.params.id, {
    ...appliedFilters.value,
    offset: offset.value,
    limit
  })
  records.value = data.records
  total.value = data.total
  await router.replace({ name: 'dataset-records', params: { id: route.params.id }, query: currentQuery() })
}

async function changePage(direction) {
  const next = offset.value + direction * limit
  offset.value = Math.min(Math.max(0, next), Math.max(0, (pageCount.value - 1) * limit))
  await reloadPage()
}

async function jumpPage() {
  const target = Math.min(Math.max(1, Number(pageInput.value) || 1), pageCount.value)
  offset.value = (target - 1) * limit
  pageInput.value = String(target)
  await reloadPage()
}

async function reloadPage() {
  loading.value = true
  try {
    await loadRecords()
  } finally {
    loading.value = false
  }
}

function applyDraftFilters() {
  appliedFilters.value = {
    search: search.value.trim(),
    energyMin: energyMin.value,
    energyMax: energyMax.value,
    atomMin: atomMin.value,
    atomMax: atomMax.value
  }
}

async function submitFilters() {
  offset.value = 0
  applyDraftFilters()
  await reloadPage()
}

async function resetFilters() {
  search.value = ''
  energyMin.value = ''
  energyMax.value = ''
  atomMin.value = ''
  atomMax.value = ''
  await submitFilters()
}

function openRecord(record) {
  router.push({
    name: 'record-detail',
    params: { id: route.params.id, recordId: record.id },
    query: currentQuery()
  })
}

function backHome() {
  router.push({ name: 'home' })
}

function openExplore() {
  router.push({ name: 'explore' })
}

function openAssistant() {
  router.push({
    name: 'assistant',
    query: {
      datasetId: route.params.id,
      contextName: detail.value?.title || route.params.id
    }
  })
}

function displayIndex(index) {
  return offset.value + index + 1
}

function recordName(record) {
  return record.materialName || record.materialId || record.sourceRecordId
}

function valueOrDash(value) {
  return value === null || value === undefined || value === '' ? '-' : value
}

function compositionTokens(composition) {
  if (!composition) return []
  return Array.from(new Set(String(composition).match(/[A-Z][a-z]?/g) || [])).slice(0, 6)
}

async function exportDataset() {
  actionError.value = ''
  if (!canDownloadDataset.value) {
    actionError.value = authUser.value.role === 'GUEST'
      ? '游客仅可查看数据；登录后的注册用户可下载单条记录，完整数据集仅管理员可下载。'
      : '当前角色为注册用户，可进入具体记录下载单条数据；完整数据集仅管理员和超级管理员可下载。'
    return
  }
  try {
    await downloadDataset(route.params.id)
  } catch (err) {
    actionError.value = err.message || '数据集导出失败'
  }
}
</script>

<template>
  <div class="page">
    <AppTopbar show-back @brand-click="backHome">
      <template #actions>
        <button class="back" @click="openAssistant">智能助手</button>
        <button class="back" @click="openExplore">数据发现</button>
        <button class="back" @click="backHome">返回数据中心</button>
      </template>
    </AppTopbar>

    <main class="shell" :style="{ '--accent': currentMeta.accent }">
      <div v-if="error" class="state error">{{ error }}</div>

      <template v-else>
        <section class="dataset-head">
          <div class="dataset-title">
            <div class="dataset-symbol">{{ currentMeta.code }}</div>
            <div>
              <p class="eyebrow">{{ currentMeta.kind }}</p>
              <h1>{{ detail?.title || route.params.id }}</h1>
              <p>{{ detail?.intro }}</p>
              <div class="method-pills">
                <span v-for="part in methodParts" :key="part">{{ part }}</span>
              </div>
              <div v-if="detail?.links?.length" class="source-links">
                <a
                  v-for="link in detail.links"
                  :key="link.url"
                  :href="link.url"
                  target="_blank"
                  rel="noopener noreferrer"
                >{{ link.label }}</a>
              </div>
            </div>
          </div>

          <div class="dataset-panel">
            <div class="metrics">
              <div>
                <strong>{{ total.toLocaleString('zh-CN') }}</strong>
                <span>{{ recordUnit }}</span>
              </div>
              <div>
                <strong>{{ detail?.atomCountRange || `${detail?.minAtoms}-${detail?.maxAtoms}` }}</strong>
                <span>原子数范围</span>
              </div>
            </div>
            <div class="element-ribbon">
              <span v-for="element in elementList.slice(0, 14)" :key="element">{{ element }}</span>
              <span v-if="elementList.length > 14">+{{ elementList.length - 14 }}</span>
            </div>
            <button class="dataset-download" @click="exportDataset">
              下载该数据集展示记录 CSV
            </button>
            <p class="download-policy">完整数据集下载权限：管理员 / 超级管理员</p>
          </div>
        </section>

        <p v-if="actionError" class="action-error">{{ actionError }}</p>

        <DatasetStatsCharts
          v-if="stats"
          :stats="stats"
          :accent="currentMeta.accent"
        />
        <p v-if="stats" class="stats-scope-note">
          图表展示整个数据集的总体分布；下方条件由后端查询接口筛选记录列表。
        </p>

        <form class="toolbar" @submit.prevent="submitFilters">
          <label class="search-field">
            <span>关键词检索</span>
            <input
              v-model="search"
              type="search"
              placeholder="名称 / material id / composition / SMILES / source id"
            >
          </label>
          <label>
            <span>{{ energyFieldLabel }}下限</span>
            <input v-model="energyMin" type="number" step="any" placeholder="min">
          </label>
          <label>
            <span>{{ energyFieldLabel }}上限</span>
            <input v-model="energyMax" type="number" step="any" placeholder="max">
          </label>
          <label>
            <span>原子数下限</span>
            <input v-model="atomMin" type="number" min="0" step="1" placeholder="min">
          </label>
          <label>
            <span>原子数上限</span>
            <input v-model="atomMax" type="number" min="0" step="1" placeholder="max">
          </label>
          <button class="apply" :disabled="loading" type="submit">检索记录</button>
          <button class="clear" :disabled="loading" type="button" @click="resetFilters">清空</button>
        </form>

        <section class="records">
          <div class="records-head">
            <div>
              <h2>记录列表 <span class="server-filter">服务端筛选</span></h2>
              <p>匹配 {{ total.toLocaleString('zh-CN') }} 条记录，当前页 {{ pageRange }}，已启用 {{ activeFilterCount }} 个筛选条件。</p>
              <p v-if="isAniDataset">ANI-GDB s03 当前按构象展开；同一分子组会出现多个构象。</p>
            </div>
            <div class="pager">
              <button :disabled="offset === 0 || loading" @click="changePage(-1)">上一页</button>
              <span>第 {{ page }} / {{ pageCount }} 页</span>
              <button :disabled="page >= pageCount || loading" @click="changePage(1)">下一页</button>
              <form @submit.prevent="jumpPage">
                <input v-model="pageInput" type="number" min="1" :max="pageCount" aria-label="跳转页码">
                <button :disabled="loading" type="submit">跳转</button>
              </form>
            </div>
          </div>

          <div v-if="loading" class="state">正在读取记录...</div>
          <div v-else-if="!records.length" class="state">没有匹配记录</div>
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>名称</th>
                  <th>Composition / SMILES</th>
                  <th>Atoms</th>
                  <th>Energy</th>
                  <th>HOMO/LUMO Gap</th>
                  <th>Charge</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(record, index) in records" :key="record.id" @click="openRecord(record)">
                  <td>
                    <span class="row-index">#{{ displayIndex(index) }}</span>
                  </td>
                  <td>
                    <strong>{{ valueOrDash(recordName(record)) }}</strong>
                    <span>{{ valueOrDash(record.sourceRecordId) }}</span>
                  </td>
                  <td>
                    <strong>{{ valueOrDash(record.composition) }}</strong>
                    <div v-if="compositionTokens(record.composition).length" class="mini-elements">
                      <span v-for="element in compositionTokens(record.composition)" :key="element">{{ element }}</span>
                    </div>
                    <span>{{ valueOrDash(record.smiles) }}</span>
                  </td>
                  <td>{{ valueOrDash(record.atomCount) }}</td>
                  <td>{{ valueOrDash(record.energy) }}</td>
                  <td>{{ valueOrDash(record.homoLumoGap) }}</td>
                  <td>{{ valueOrDash(record.charge) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

button {
  font: inherit;
  cursor: pointer;
}

.back {
  border: 1px solid var(--vs-border-strong);
  background: color-mix(in srgb, var(--vs-primary) 13%, var(--vs-card));
  color: var(--vs-primary);
  border-radius: var(--vs-radius-md);
  padding: 9px 13px;
  font-weight: 700;
}

.shell {
  width: min(1880px, calc(100% - 48px));
  margin: 0 auto;
  padding: 28px 0 54px;
}

.dataset-head,
.toolbar,
.records,
.state {
  background: var(--vs-card);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
}

.dataset-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  gap: 24px;
  padding: 28px;
  overflow: hidden;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 7%, var(--vs-card)), var(--vs-card) 52%),
    var(--vs-card);
}

.dataset-title {
  display: flex;
  gap: 18px;
  min-width: 0;
}

.dataset-symbol {
  width: 58px;
  height: 58px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: var(--accent);
  color: #fff;
  box-shadow: 0 14px 28px color-mix(in srgb, var(--accent) 28%, transparent);
  font-size: 15px;
  font-weight: 900;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-size: 13px;
  font-weight: 800;
  text-transform: uppercase;
}

h1,
h2 {
  margin: 0;
  color: var(--vs-text);
  letter-spacing: 0;
}

.dataset-head p:not(.eyebrow) {
  max-width: 880px;
  margin: 12px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.65;
}

.method-pills,
.element-ribbon,
.mini-elements,
.source-links {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.method-pills {
  margin-top: 16px;
}

.source-links {
  margin-top: 12px;
}

.method-pills span,
.element-ribbon span,
.mini-elements span {
  border: 1px solid color-mix(in srgb, var(--accent) 25%, var(--vs-border));
  background: color-mix(in srgb, var(--accent) 9%, var(--vs-card));
  color: var(--accent);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 800;
}

.source-links a {
  padding: 7px 11px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  color: var(--accent);
  text-decoration: none;
  font-size: 12px;
  font-weight: 800;
}

.source-links a:hover {
  background: color-mix(in srgb, var(--accent) 8%, var(--vs-card));
}

.dataset-panel {
  display: grid;
  gap: 14px;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.metrics div {
  background: var(--vs-card-soft);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  padding: 16px;
}

.metrics strong {
  display: block;
  color: var(--accent);
  font-size: 24px;
}

.metrics span,
.records-head p,
td span {
  color: var(--vs-text-secondary);
}

.element-ribbon {
  min-height: 96px;
  align-content: flex-start;
  padding: 16px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 8%, var(--vs-card-soft)), var(--vs-card-soft));
}

.dataset-download {
  height: 42px;
  border: 1px solid var(--accent);
  border-radius: var(--vs-radius-md);
  background: var(--accent);
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.dataset-panel .download-policy {
  margin: -4px 0 0;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  text-align: center;
}

.action-error {
  margin: 12px 0 0;
  padding: 11px 14px;
  border: 1px solid #f1caca;
  border-radius: var(--vs-radius-md);
  background: #fff3f3;
  color: #cc5656;
  font-size: 13px;
}

.stats-scope-note {
  margin: 12px 0 0;
  padding: 10px 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  font-size: 13px;
}

.toolbar {
  margin-top: 16px;
  padding: 18px;
  display: grid;
  grid-template-columns: minmax(280px, 1.8fr) repeat(4, minmax(120px, 1fr)) auto auto;
  gap: 12px;
  align-items: end;
}

.toolbar label {
  display: grid;
  gap: 8px;
  color: var(--vs-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

input {
  height: 42px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-elevated);
  color: var(--vs-text);
  padding: 0 13px;
  outline: none;
  min-width: 0;
}

input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 16%, transparent);
}

.apply,
.clear,
.pager button {
  border: 1px solid color-mix(in srgb, var(--accent) 28%, var(--vs-border-strong));
  background: color-mix(in srgb, var(--accent) 10%, var(--vs-card));
  color: var(--accent);
  border-radius: var(--vs-radius-md);
  padding: 9px 14px;
  font-weight: 800;
}

.apply {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.apply:disabled,
.clear:disabled {
  opacity: 0.5;
  cursor: wait;
}

.records {
  margin-top: 16px;
  overflow: hidden;
}

.records-head {
  padding: 18px 20px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--vs-border);
  align-items: center;
}

.records-head p {
  margin: 6px 0 0;
  font-size: 13px;
}

.server-filter {
  display: inline-flex;
  margin-left: 8px;
  padding: 4px 7px;
  vertical-align: middle;
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--accent) 10%, var(--vs-card));
  color: var(--accent);
  font-size: 11px;
  font-weight: 800;
}

.pager,
.pager form {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pager span {
  white-space: nowrap;
  color: var(--vs-text-secondary);
}

.pager input {
  width: 76px;
}

.pager button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 980px;
}

th,
td {
  text-align: left;
  padding: 13px 16px;
  border-bottom: 1px solid var(--vs-border);
}

th {
  color: var(--vs-text-secondary);
  background: var(--vs-card-soft);
  font-size: 12px;
  text-transform: uppercase;
}

td {
  color: var(--vs-text);
  font-size: 14px;
  vertical-align: top;
}

.row-index {
  display: inline-flex;
  height: 26px;
  align-items: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 10%, var(--vs-card));
  color: var(--accent);
  padding: 0 10px;
  font-size: 12px;
  font-weight: 900;
}

td strong {
  display: block;
  max-width: 390px;
  overflow-wrap: anywhere;
}

td span {
  display: block;
  max-width: 390px;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.mini-elements {
  margin-top: 8px;
}

.mini-elements span {
  display: inline-flex;
  max-width: none;
  margin: 0;
  padding: 3px 7px;
  font-size: 11px;
  line-height: 1.2;
}

tbody tr {
  cursor: pointer;
}

tbody tr:hover {
  background: color-mix(in srgb, var(--accent) 8%, var(--vs-card));
}

.state {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--vs-text-secondary);
}

.state.error {
  color: #cc5656;
}

@media (max-width: 1180px) {
  .dataset-head {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 980px) {
  .records-head {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-field {
    grid-column: 1 / -1;
  }

  .pager {
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .dataset-title {
    flex-direction: column;
  }

  .toolbar,
  .metrics {
    grid-template-columns: 1fr;
  }
}
</style>
