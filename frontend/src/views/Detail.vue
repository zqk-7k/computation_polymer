<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadRecord, fetchDatasetRecord } from '../api'
import { authUser, canDownloadSingle } from '../auth/session'
import Molecule3DViewer from '../components/Molecule3DViewer.vue'
import AppTopbar from '../components/AppTopbar.vue'

const route = useRoute()
const router = useRouter()

const record = ref(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const activeTab = ref('structure')
const showLattice = ref(true)
const showBonds = ref(true)

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

const atomColors = {
  Ag: '#cbd5e1',
  As: '#a78bfa',
  B: '#f97316',
  Ba: '#84cc16',
  Bi: '#8b5cf6',
  Br: '#8b5cf6',
  C: '#22c55e',
  Ca: '#38bdf8',
  Cd: '#94a3b8',
  Cl: '#16a34a',
  Co: '#2563eb',
  Cu: '#f59e0b',
  F: '#06b6d4',
  Ga: '#8fbf9f',
  Ge: '#7aa7a8',
  H: '#e2e8f0',
  I: '#7c3aed',
  Li: '#ef4444',
  Mg: '#0ea5e9',
  N: '#60a5fa',
  Na: '#f97316',
  Ni: '#64748b',
  O: '#f87171',
  P: '#f59e0b',
  Pb: '#78909c',
  S: '#eab308',
  Si: '#14b8a6',
  Sr: '#8bc34a',
  Te: '#9c7aa8',
  Ti: '#94a3b8',
  Zn: '#38bdf8'
}

const baseTabs = [
  { id: 'structure', label: '结构坐标' },
  { id: 'simulation', label: '计算参数' },
  { id: 'thermal', label: '热力学' },
  { id: 'mechanical', label: '力学性能' },
  { id: 'electronic', label: '电子结构' },
  { id: 'meta', label: '元数据' }
]

const currentMeta = computed(() => datasetMeta[route.params.id] || { code: 'DFT', kind: '计算数据', accent: '#2f6fed' })

const displayName = computed(() => {
  if (!record.value) return ''
  return record.value.materialName || record.value.materialId || record.value.sourceRecordId
})

const extraProperties = computed(() => Object.entries(record.value?.extraProperties || {}))

const elementCounts = computed(() => {
  const counts = new Map()
  ;(record.value?.atoms || []).forEach(atom => {
    counts.set(atom.element, (counts.get(atom.element) || 0) + 1)
  })
  const entries = Array.from(counts.entries()).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
  const max = Math.max(...entries.map(([, count]) => count), 1)
  return entries.map(([symbol, count]) => ({
    symbol,
    count,
    color: atomColors[symbol] || '#a78bfa',
    width: `${Math.max(12, (count / max) * 100)}%`
  }))
})
const hasLattice = computed(() => Array.isArray(record.value?.lattice) && record.value.lattice.length === 3)

const stats = computed(() => [
  { label: '原子数', value: record.value?.atomCount },
  { label: '元素种类', value: elementCounts.value.length || '' },
  { label: '总能量', value: record.value?.energy },
  { label: 'HOMO-LUMO Gap', value: record.value?.homoLumoGap },
  { label: '电荷 / 自旋', value: [record.value?.charge, record.value?.spin].filter(Boolean).join(' / ') }
].filter(item => hasValue(item.value)))

const headTags = computed(() => [
  record.value?.calculationSoftware || record.value?.calculationPlatform,
  record.value?.doi
].filter(hasValue))

const structureFields = computed(() => fields([
  ['Source ID', record.value?.sourceRecordId],
  ['Material ID', record.value?.materialId],
  ['Rg', record.value?.radiusOfGyration],
  ['SMILES', record.value?.smiles]
]))

const simulationFields = computed(() => fields([
  ['计算软件', record.value?.calculationSoftware],
  ['力场', record.value?.forceField],
  ['模拟类型', record.value?.simulationType],
  ['系综', record.value?.ensemble],
  ['温度', record.value?.temperature],
  ['计算平台', record.value?.calculationPlatform]
]))

const thermalFields = computed(() => fields([
  ['密度', record.value?.density],
  ['Tg', record.value?.glassTransitionTemperatureTg],
  ['链构象', record.value?.chainConformation]
]))

const mechanicalFields = computed(() => fields([
  ['杨氏模量', record.value?.youngsModulus],
  ['拉伸强度', record.value?.tensileStrength],
  ['验证状态', record.value?.validatedStatus]
]))

const electronicFields = computed(() => fields([
  ['HOMO', record.value?.homo],
  ['LUMO', record.value?.lumo],
  ['HOMO-LUMO Gap', record.value?.homoLumoGap],
  ['总能量', record.value?.energy],
  ['电荷', record.value?.charge],
  ['自旋', record.value?.spin]
]))

const metaFields = computed(() => fields([
  ['数据集', record.value?.datasetId],
  ['数据库记录 ID', record.value ? `#${record.value.id}` : ''],
  ['类别', record.value?.category],
  ['DOI', record.value?.doi],
  ['聚合度', record.value?.polymerizationDegree],
  ['警告', record.value?.warnings?.length ? record.value.warnings.join('；') : '']
]))

const tabs = computed(() => baseTabs.filter(tab => {
  if (tab.id === 'structure' || tab.id === 'meta') return true
  if (tab.id === 'simulation') return simulationFields.value.length > 0
  if (tab.id === 'thermal') return thermalFields.value.length > 0
  if (tab.id === 'mechanical') return mechanicalFields.value.length > 0
  return electronicFields.value.length > 0
}))

onMounted(loadRecord)

async function loadRecord() {
  loading.value = true
  error.value = ''
  try {
    record.value = await fetchDatasetRecord(route.params.id, route.params.recordId)
  } catch (err) {
    error.value = err.message || '记录加载失败'
  } finally {
    loading.value = false
  }
}

function back() {
  router.push({ name: 'dataset-records', params: { id: route.params.id }, query: route.query })
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
      recordId: route.params.recordId,
      contextName: displayName.value || `记录 #${route.params.recordId}`
    }
  })
}

function valueOrDash(value) {
  return value === null || value === undefined || value === '' ? '暂无数据' : value
}

function hasValue(value) {
  return value !== null && value !== undefined && value !== ''
}

function fields(items) {
  return items.filter(([, value]) => hasValue(value)).map(([label, value]) => ({ label, value }))
}

function formatNumber(value, digits = 6) {
  const n = Number(value)
  if (!Number.isFinite(n)) return valueOrDash(value)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: digits })
}

async function downloadEntry() {
  actionError.value = ''
  if (!canDownloadSingle.value) {
    actionError.value = authUser.value.role === 'GUEST'
      ? '游客仅有查看权限；注册登录后可下载此条记录。'
      : '当前角色不能下载此条记录。'
    return
  }
  try {
    await downloadRecord(record.value.datasetId, record.value.id)
  } catch (err) {
    actionError.value = err.message || '记录下载失败'
  }
}
</script>

<template>
  <div class="page">
    <AppTopbar
      show-back
      :back-fallback="{ name: 'dataset-records', params: { id: route.params.id }, query: route.query }"
      @brand-click="backHome"
    >
      <template #actions>
        <button class="back" @click="openAssistant">询问此结构</button>
        <button class="back" @click="openExplore">数据发现</button>
      </template>
    </AppTopbar>

    <main class="shell" :style="{ '--accent': currentMeta.accent }">
      <div v-if="loading" class="state">正在读取结构记录...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>

      <template v-else-if="record">
        <nav class="breadcrumb">
          <button @click="backHome">数据中心</button>
          <span>/</span>
          <button @click="back">{{ record.datasetName || record.datasetId }}</button>
          <span>/</span>
          <strong>{{ displayName }}</strong>
        </nav>

        <section class="record-head">
          <div class="record-title">
            <div class="record-symbol">{{ currentMeta.code }}</div>
            <div>
              <p class="eyebrow">{{ currentMeta.kind }} · {{ record.datasetName || record.datasetId }}</p>
              <h1>{{ valueOrDash(displayName) }}</h1>
              <p>{{ valueOrDash(record.composition || record.smiles) }}</p>
              <div v-if="headTags.length" class="head-tags">
                <span v-for="tag in headTags" :key="tag">{{ tag }}</span>
              </div>
            </div>
          </div>
          <button class="download" @click="downloadEntry">下载此条记录 JSON</button>
        </section>
        <p v-if="actionError" class="action-error">{{ actionError }}</p>

        <section class="stats">
          <div v-for="item in stats" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ valueOrDash(item.value) }}</strong>
          </div>
        </section>

        <section class="layout">
          <article class="main-panel">
            <div class="tabs">
              <button
                v-for="tab in tabs"
                :key="tab.id"
                :class="{ active: activeTab === tab.id }"
                @click="activeTab = tab.id"
              >
                {{ tab.label }}
              </button>
            </div>

            <div class="tab-body">
              <div v-show="activeTab === 'structure'" class="structure-grid">
                <div>
                  <h2>结构坐标</h2>
                  <p class="muted">
                    右侧 3D 视图使用原始坐标居中显示；材料/CIF 数据若提供 lattice，会显示真实周期晶胞，否则显示坐标包围盒辅助观察。
                  </p>
                  <div v-if="structureFields.length" class="field-grid">
                    <div v-for="item in structureFields" :key="item.label">
                      <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
                    </div>
                  </div>
                </div>

                <div class="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>元素</th>
                        <th>X</th>
                        <th>Y</th>
                        <th>Z</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="atom in record.atoms.slice(0, 80)" :key="atom.index">
                        <td>{{ atom.index }}</td>
                        <td><span class="atom-pill">{{ atom.element }}</span></td>
                        <td>{{ formatNumber(atom.x) }}</td>
                        <td>{{ formatNumber(atom.y) }}</td>
                        <td>{{ formatNumber(atom.z) }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <p v-if="record.atoms.length > 80" class="muted">仅预览前 80 个原子，下载的单条记录文件包含完整可用字段与坐标。</p>
                </div>
              </div>

              <div v-show="activeTab === 'simulation'" class="property-grid">
                <div v-for="item in simulationFields" :key="item.label">
                  <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
                </div>
              </div>

              <div v-show="activeTab === 'thermal'" class="property-grid">
                <div v-for="item in thermalFields" :key="item.label">
                  <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
                </div>
              </div>

              <div v-show="activeTab === 'mechanical'" class="property-grid">
                <div v-for="item in mechanicalFields" :key="item.label">
                  <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
                </div>
              </div>

              <div v-show="activeTab === 'electronic'" class="property-grid">
                <div v-for="item in electronicFields" :key="item.label">
                  <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
                </div>
              </div>

              <div v-show="activeTab === 'meta'" class="meta-stack">
                <div class="property-grid">
                  <div v-for="item in metaFields" :key="item.label">
                    <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
                  </div>
                </div>
                <div v-if="extraProperties.length" class="extra-panel">
                  <h2>补充计算属性</h2>
                  <div class="property-grid">
                    <div v-for="[key, value] in extraProperties" :key="key">
                      <span>{{ key }}</span>
                      <strong>{{ valueOrDash(value) }}</strong>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </article>

          <aside class="side-panel">
            <div class="side-head">
              <h2>3D 结构</h2>
              <span>{{ hasLattice ? 'periodic cell' : (record.atoms.length ? `${record.atoms.length} atoms` : 'no coordinates') }}</span>
            </div>
            <div v-if="record.atoms.length" class="viewer-controls">
              <label>
                <input v-model="showBonds" type="checkbox">
                <span>键连线</span>
              </label>
              <label :class="{ disabled: !hasLattice }">
                <input v-model="showLattice" type="checkbox" :disabled="!hasLattice">
                <span>周期晶胞</span>
              </label>
            </div>
            <Molecule3DViewer
              v-if="record.atoms.length"
              :atoms="record.atoms"
              :lattice="record.lattice"
              :show-lattice="showLattice"
              :show-bonds="showBonds"
            />
            <div v-else class="empty-structure">
              当前记录没有原子三维坐标；可查看 SMILES/PSMILES 和计算属性。
            </div>

            <div v-if="elementCounts.length" class="composition-panel">
              <div class="side-head">
                <h2>元素组成</h2>
                <span>{{ elementCounts.length }} types</span>
              </div>
              <div class="element-legend">
                <span
                  v-for="item in elementCounts"
                  :key="`legend-${item.symbol}`"
                >
                  <i :style="{ background: item.color }"></i>
                  {{ item.symbol }}
                </span>
              </div>
              <div class="composition-bars">
                <div v-for="item in elementCounts" :key="item.symbol" class="composition-row">
                  <span><i :style="{ background: item.color }"></i>{{ item.symbol }}</span>
                  <div><i :style="{ width: item.width }"></i></div>
                  <strong>{{ item.count }}</strong>
                </div>
              </div>
            </div>

            <div v-if="record.atoms.length" class="legend">
              <span>{{ hasLattice ? '晶胞线框' : '浅色包围盒' }}</span>
              <strong>{{ hasLattice ? '来自 structure_json.lattice' : '坐标范围辅助线' }}</strong>
            </div>
          </aside>
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

.back,
.download {
  border: 1px solid color-mix(in srgb, var(--accent, var(--vs-primary)) 30%, var(--vs-border-strong));
  background: color-mix(in srgb, var(--accent, var(--vs-primary)) 11%, var(--vs-card));
  color: var(--accent, var(--vs-primary));
  border-radius: var(--vs-radius-md);
  padding: 9px 13px;
  font-weight: 800;
  text-decoration: none;
}

.shell {
  width: min(1880px, calc(100% - 48px));
  margin: 0 auto;
  padding: 28px 0 54px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--vs-text-secondary);
  font-size: 13px;
}

.breadcrumb button {
  border: 0;
  background: transparent;
  color: var(--accent);
  padding: 0;
}

.breadcrumb strong {
  color: var(--vs-text);
  overflow-wrap: anywhere;
}

.record-head,
.stats,
.main-panel,
.side-panel,
.state {
  background: var(--vs-card);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
}

.record-head {
  padding: 26px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 8%, var(--vs-card)), var(--vs-card) 58%),
    var(--vs-card);
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

.record-title {
  display: flex;
  gap: 18px;
  min-width: 0;
}

.record-symbol {
  width: 60px;
  height: 60px;
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

h1 {
  font-size: clamp(26px, 4vw, 40px);
  overflow-wrap: anywhere;
}

.record-head p:not(.eyebrow),
.muted {
  color: var(--vs-text-secondary);
  line-height: 1.65;
}

.head-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.head-tags span,
.atom-pill {
  border: 1px solid color-mix(in srgb, var(--accent) 25%, var(--vs-border));
  background: color-mix(in srgb, var(--accent) 9%, var(--vs-card));
  color: var(--accent);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 800;
}

.stats {
  margin-top: 16px;
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.stats div,
.field-grid div,
.property-grid div,
.legend {
  background: var(--vs-card-soft);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  padding: 14px;
}

.stats span,
.field-grid span,
.property-grid span,
.legend span {
  display: block;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  margin-bottom: 7px;
}

.stats strong,
.field-grid strong,
.property-grid strong,
.legend strong {
  color: var(--vs-text);
  overflow-wrap: anywhere;
}

.layout {
  margin-top: 16px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 16px;
  align-items: start;
}

.tabs {
  padding: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  border-bottom: 1px solid var(--vs-border);
}

.tabs button {
  border: 1px solid color-mix(in srgb, var(--accent) 28%, var(--vs-border-strong));
  background: color-mix(in srgb, var(--accent) 10%, var(--vs-card));
  color: var(--accent);
  border-radius: 999px;
  padding: 8px 13px;
  font-weight: 700;
}

.tabs button.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.tab-body {
  padding: 20px;
}

.structure-grid {
  display: grid;
  gap: 18px;
}

.meta-stack,
.extra-panel {
  display: grid;
  gap: 16px;
}

.field-grid,
.property-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.field-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 560px;
}

th,
td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--vs-border);
}

th {
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  font-size: 12px;
}

td {
  color: var(--vs-text);
  font-size: 13px;
}

.atom-pill {
  display: inline-flex;
  padding: 3px 8px;
  font-size: 11px;
}

.side-panel {
  padding: 16px;
  position: sticky;
  top: 84px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.side-head span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 800;
}

.viewer-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.viewer-controls label {
  display: inline-flex;
  height: 30px;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--vs-border));
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 7%, var(--vs-card));
  color: var(--vs-text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.viewer-controls label.disabled {
  opacity: 0.52;
}

.viewer-controls input {
  width: 14px;
  height: 14px;
  accent-color: var(--accent);
}

.composition-panel {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.composition-bars {
  display: grid;
  gap: 10px;
}

.element-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 12px;
}

.element-legend span {
  display: inline-flex;
  height: 28px;
  align-items: center;
  gap: 7px;
  padding: 0 9px;
  border: 1px solid var(--vs-border);
  border-radius: 999px;
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.element-legend i,
.composition-row span i {
  display: inline-block;
  flex: 0 0 auto;
  width: 10px;
  height: 10px;
  border: 1px solid rgba(37, 49, 59, 0.14);
  border-radius: 50%;
}

.composition-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 42px;
  align-items: center;
  gap: 10px;
}

.composition-row span,
.composition-row strong {
  color: var(--vs-text);
  font-size: 12px;
  font-weight: 900;
}

.composition-row span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.composition-row div {
  height: 9px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-border) 70%, transparent);
}

.composition-row i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent), color-mix(in srgb, var(--accent) 35%, var(--vs-card)));
}

.legend {
  margin-top: 12px;
}

.empty-structure {
  min-height: 260px;
  background: var(--vs-card-soft);
  border: 1px dashed var(--vs-border-strong);
  border-radius: 8px;
  color: var(--vs-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  line-height: 1.7;
  padding: 18px;
}

.state {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--vs-text-secondary);
}

.state.error {
  color: #cc5656;
}

@media (max-width: 1180px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .side-panel {
    position: static;
  }
}

@media (max-width: 920px) {
  .record-head {
    flex-direction: column;
  }

  .stats,
  .field-grid,
  .property-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .record-title {
    flex-direction: column;
  }

  .stats,
  .field-grid,
  .property-grid {
    grid-template-columns: 1fr;
  }
}
</style>
