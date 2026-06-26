<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchDatasetQualityReport,
  fetchDatasetPublication,
  fetchDatasetRecords,
  fetchDatasets,
  fetchQualityOverview,
  previewDatasetFile,
  submitDatasetSource,
  updateDatasetPublication,
  validateDatasetLinks
} from '../api'
import { isAdminOrSuperAdmin, isAuthenticated, isSuperAdmin } from '../auth/session'
import AppTopbar from '../components/AppTopbar.vue'
import CoveragePanel from '../components/dashboard/CoveragePanel.vue'
import ElementSpectrum from '../components/dashboard/ElementSpectrum.vue'
import Hero3DScene from '../components/dashboard/Hero3DScene.vue'
import KpiCard from '../components/dashboard/KpiCard.vue'

const route = useRoute()
const router = useRouter()
const activeTab = ref(normalizeTab(route.query.tab))
const datasets = ref([])
const loading = ref(true)
const error = ref('')
const quality = ref(null)
const qualityLoading = ref(false)
const qualityError = ref('')
const preflightError = ref('')
const preflightNotice = ref('')
const linkChecking = ref(false)
const linkResult = ref(null)
const fileChecking = ref(false)
const filePreview = ref(null)
const intakeSubmitting = ref(false)
const publicationRows = ref([])
const publicationLoading = ref(false)
const publicationNote = ref('')
const qualityReport = ref(null)
const qualityReportLoading = ref(false)
const qualityReportError = ref('')
const linkForm = reactive({
  doi: '',
  paperUrl: '',
  dataUrl: ''
})
const canViewQualityReview = computed(() => isAdminOrSuperAdmin.value)

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
  hydrocarbons_gap_ch: { code: 'CH', kind: '反应势训练', accent: '#6f8f4f' },
  matbench_v01_dielectric: { code: 'Di', kind: '介电基准', accent: '#4b8f86' },
  matbench_v01_jdft2d: { code: 'J2', kind: '二维剥离能', accent: '#b28a3c' },
  matbench_v01_phonons: { code: 'Pn', kind: '声子基准', accent: '#7b8fbf' },
  matbench_v01_perovskites: { code: 'Pv', kind: '钙钛矿', accent: '#6f9a5f' },
  matbench_v01_log_gvrh: { code: 'G', kind: '剪切模量', accent: '#9b725f' },
  matbench_v01_log_kvrh: { code: 'K', kind: '体积模量', accent: '#8b7bb8' },
  qm9_molecular_dft: { code: 'Q9', kind: '分子DFT', accent: '#5b8fb0' }
}

const domainConfig = [
  { key: 'molecule', title: '分子构象', code: 'Mol', ids: ['ani_gdb_s03', 'ani1x_less_is_more', 'hydrocarbons_gap_ch', 'qm9_molecular_dft'], color: '#2f6fed', description: '小分子 DFT 构象、能量、力和性质标签', detail: '用于分子势函数、性质预测和主动学习候选池构建。' },
  { key: 'polymer', title: '聚合物', code: 'Poly', ids: ['data0000_aselmdb', 'openpoly_calculated', 'polymer_genome_1073'], color: '#16b5c8', description: '聚合物构象、片段、性质与标签数据', detail: '面向高分子 AI 设计、热力学性质预测和结构-性质关联分析。' },
  { key: 'crystal', title: '晶体 / 二维', code: 'Mat', ids: ['twod_matpedia', 'jarvis_dft_3d', 'jarvis_dft_2d'], color: '#19509a', description: '晶体、二维材料结构和 DFT 性质', detail: '适合做形成能、能带、二维材料筛选和材料空间覆盖展示。' },
  { key: 'reaction', title: '反应路径', code: 'Rxn', ids: ['transition1x'], color: '#0892a5', description: '反应路径图像、能量与力', detail: '用于反应机器学习势函数和过渡态附近结构建模。' },
  { key: 'mof', title: 'MOF', code: 'MOF', ids: ['qmof_database'], color: '#047a8a', description: '多孔框架结构与电子性质', detail: '用于多孔材料、吸附筛选和框架结构性质分析。' },
  { key: 'benchmark', title: '基准性质', code: 'Bench', ids: ['matbench_wbm_summary', 'matbench_mp_energies', 'matbench_phonondb_pbe_103', 'matbench_v01_dielectric', 'matbench_v01_jdft2d', 'matbench_v01_phonons', 'matbench_v01_perovskites', 'matbench_v01_log_gvrh', 'matbench_v01_log_kvrh'], color: '#6d5bd7', description: 'Matbench / MP 结构性质基准', detail: '用于模型评测、性质回归、稳定性判断和跨数据集对比。' }
]

const expertConcerns = [
  { title: '有什么数据', text: '明确 VASP 计算数据、聚合物结构、性质标签、计算参数和原始文件的边界。' },
  { title: '质量怎么保证', text: '用收敛状态、参数完整性、重复/异常标记和版本记录给每条数据打可信标签。' },
  { title: '如何融通共享', text: '字段模板、API 和下载格式对齐国家新材料数据中心的数据汇交要求。' },
  { title: '谁会真正使用', text: '围绕固态电解质、介电材料、膜材料等场景提供可检索、可筛选、可追溯结果。' }
]

const evidenceSteps = [
  { step: '01', title: '任务生成', text: '结构来源、目标性质、INCAR/KPOINTS/POTCAR 模板进入任务队列。' },
  { step: '02', title: 'VASP 计算', text: '记录软件版本、算力环境、提交脚本、收敛阈值和计算日志。' },
  { step: '03', title: '自动解析', text: '从 vasprun.xml、OUTCAR、CONTCAR 提取结构、能量、力、带隙、DOS 等字段。' },
  { step: '04', title: '质量校验', text: '检查收敛、单位、有效数字、字段缺失、重复结构和异常能量。' },
  { step: '05', title: '标准入库', text: '写入计算元数据、性质数据、原始文件索引和可追溯任务 ID。' }
]

const demoScenes = [
  { title: '按应用找数据', text: '输入目标性质，如高介电/低带隙/界面稳定性，返回候选结构和计算证据。' },
  { title: '按质量筛数据', text: '筛选已收敛、参数完整、可复现的金级 VASP 数据，用于模型训练。' },
  { title: '按缺口补计算', text: '发现关键性质缺失后生成新 VASP 任务，完成后自动解析并回流数据库。' }
]

onMounted(async () => {
  try {
    datasets.value = await fetchDatasets()
  } catch (err) {
    error.value = err.message || '数据加载失败'
  } finally {
    loading.value = false
  }
  if (activeTab.value === 'quality') {
    loadQuality()
  }
})

async function loadQuality() {
  if (quality.value || qualityLoading.value) return
  qualityLoading.value = true
  qualityError.value = ''
  try {
    quality.value = await fetchQualityOverview()
    if (isSuperAdmin.value) {
      loadPublication()
    }
  } catch (err) {
    qualityError.value = err.message || '质量验证数据加载失败'
  } finally {
    qualityLoading.value = false
  }
}

function refreshQuality() {
  quality.value = null
  loadQuality()
}

async function loadPublication() {
  if (!isSuperAdmin.value || publicationLoading.value) return
  publicationLoading.value = true
  try {
    publicationRows.value = await fetchDatasetPublication()
  } catch (err) {
    preflightError.value = err.message || '发布状态加载失败'
  } finally {
    publicationLoading.value = false
  }
}

async function runLinkValidation() {
  preflightError.value = ''
  preflightNotice.value = ''
  if (!isAuthenticated.value) {
    router.push({ name: 'login', query: { redirect: '/?tab=quality' } })
    return
  }
  linkChecking.value = true
  try {
    linkResult.value = await validateDatasetLinks({ ...linkForm })
    preflightNotice.value = '链接真实性验证完成。'
  } catch (err) {
    preflightError.value = err.message || '链接验证失败'
  } finally {
    linkChecking.value = false
  }
}

async function handlePreviewFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  preflightError.value = ''
  preflightNotice.value = ''
  if (!isAuthenticated.value) {
    router.push({ name: 'login', query: { redirect: '/?tab=quality' } })
    event.target.value = ''
    return
  }
  fileChecking.value = true
  try {
    filePreview.value = await previewDatasetFile(file)
    preflightNotice.value = '文件字段预检完成，可生成审核申请。'
  } catch (err) {
    preflightError.value = err.message || '文件预检失败'
  } finally {
    fileChecking.value = false
    event.target.value = ''
  }
}

async function submitPreflightForReview() {
  preflightError.value = ''
  preflightNotice.value = ''
  if (!isAuthenticated.value) {
    router.push({ name: 'login', query: { redirect: '/?tab=quality' } })
    return
  }
  if (!filePreview.value && !linkResult.value) {
    preflightError.value = '请先完成链接验证或文件预检。'
    return
  }
  intakeSubmitting.value = true
  try {
    const preview = filePreview.value
    const linkScore = linkResult.value ? `链接验证 ${linkResult.value.score} 分` : '未做链接验证'
    const fileScore = preview ? `文件预检 ${preview.score} 分` : '未上传文件'
    const fields = preview
      ? preview.fields.map(field => `${field.name}${field.mappedField ? `=>${field.mappedField}` : ''}`).join('；')
      : '待管理员核对'
    const description = [
      '由质量验证页面自动生成的候选数据集审核申请。',
      linkScore,
      fileScore,
      preview ? preview.summary : '',
      preview ? `缺失字段：${preview.missingFields.join('；')}` : '',
      preview ? `建议：${preview.recommendations.join('；')}` : ''
    ].filter(Boolean).join('\n')
    await submitDatasetSource({
      datasetName: preview?.filename || '待命名数据集',
      dataType: '待分类',
      description,
      paperUrl: linkForm.paperUrl || linkForm.doi,
      dataUrl: linkForm.dataUrl,
      dataFormat: preview?.format || '待识别',
      license: '待管理员确认',
      providedFields: fields.slice(0, 1000)
    })
    preflightNotice.value = '已提交超级管理员审核，可在数据接入中心查看进度。'
  } catch (err) {
    preflightError.value = err.message || '提交审核失败'
  } finally {
    intakeSubmitting.value = false
  }
}

async function togglePublication(row) {
  preflightError.value = ''
  preflightNotice.value = ''
  try {
    const updated = await updateDatasetPublication(row.datasetId, !row.published, publicationNote.value)
    publicationRows.value = publicationRows.value.map(item => item.datasetId === updated.datasetId ? updated : item)
    preflightNotice.value = updated.published ? '数据集已发布。' : '数据集已隐藏，普通用户将不可见。'
    datasets.value = await fetchDatasets()
    quality.value = null
    await loadQuality()
  } catch (err) {
    preflightError.value = err.message || '更新发布状态失败'
  }
}

async function openQualityReport(datasetId) {
  qualityReportError.value = ''
  if (!canViewQualityReview.value) {
    qualityReportError.value = '仅管理员和超级管理员可查看完整质量报告。'
    return
  }
  qualityReportLoading.value = true
  qualityReport.value = null
  try {
    qualityReport.value = await fetchDatasetQualityReport(datasetId)
  } catch (err) {
    qualityReportError.value = err.message || '质量报告加载失败'
  } finally {
    qualityReportLoading.value = false
  }
}

function closeQualityReport() {
  qualityReport.value = null
  qualityReportError.value = ''
}

const totalConformers = computed(() => datasets.value.reduce((sum, item) => sum + Number(item.totalConformers || 0), 0))
const displayRecordTotal = computed(() => datasets.value.reduce((sum, item) => sum + Number(item.displayRecords || item.recordCount || 0), 0) || 919611)
const heroKpis = computed(() => [
  { label: '独立数据集', value: '21', note: `${datasets.value.length || 21} 个已接入目录` },
  { label: '原始构象/结构', value: '15.5M', note: `${formatCompact(totalConformers.value)} 当前统计规模` },
  { label: '展示记录', value: '919,611', note: `${formatCompact(displayRecordTotal.value)} 条可检索记录` }
])

const elementEntries = computed(() => {
  const counts = new Map()
  const recordCounts = new Map()
  datasets.value.forEach(item => {
    ;(item.elements || []).forEach(element => {
      counts.set(element, (counts.get(element) || 0) + 1)
      recordCounts.set(element, (recordCounts.get(element) || 0) + Number(item.totalConformers || 0))
    })
  })
  const priority = ['H', 'C', 'N', 'O', 'F', 'S', 'Cl', 'P', 'B', 'Si', 'Ti', 'Cu', 'Zn', 'Cd', 'Ag', 'I']
  const ordered = Array.from(counts.entries()).sort((a, b) => {
    const ai = priority.indexOf(a[0])
    const bi = priority.indexOf(b[0])
    if (ai !== -1 || bi !== -1) return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi)
    return b[1] - a[1] || a[0].localeCompare(b[0])
  })
  const max = Math.max(...ordered.map(([, count]) => count), 1)
  return ordered.slice(0, 18).map(([symbol, count]) => ({
    symbol,
    count,
    datasetCount: count,
    recordCount: recordCounts.get(symbol) || 0,
    recordLabel: formatCompact(recordCounts.get(symbol) || 0),
    featured: count >= Math.max(3, max - 1),
    height: `${30 + (count / max) * 56}px`
  }))
})

const domainCards = computed(() => domainConfig.map(domain => {
  const items = datasets.value.filter(item => domain.ids.includes(item.id))
  const total = items.reduce((sum, item) => sum + Number(item.totalConformers || 0), 0)
  return {
    ...domain,
    count: items.length,
    total
  }
}))

const coverageCards = computed(() => domainConfig.map(domain => {
  const items = datasets.value.filter(item => domain.ids.includes(item.id))
  const total = items.reduce((sum, item) => sum + Number(item.totalConformers || 0), 0)
  return {
    ...domain,
    count: items.length,
    total,
    totalLabel: formatCompact(total),
    datasetNames: items.map(item => item.name).join(' / ') || '待接入',
    detail: `${domain.detail} 当前包含 ${items.length} 个数据集，覆盖 ${formatCompact(total)} 条原始构象/结构。`
  }
}))

const modelTasks = [
  {
    title: '机器学习势函数',
    tag: 'Energy / Force',
    text: '面向分子构象、聚合物片段和反应路径，使用能量与力标签训练快速势函数，用于替代部分高成本 DFT 计算。',
    metrics: ['能量 MAE', '力 MAE', '外推置信度']
  },
  {
    title: '性质预测模型',
    tag: 'Property',
    text: '围绕 band gap、HOMO/LUMO、介电常数、形成能等可用标签，构建材料性质筛选模型。',
    metrics: ['回归误差', '排序命中率', '适用域']
  },
  {
    title: '主动学习闭环',
    tag: 'Active Learning',
    text: '用模型不确定性发现缺口结构，生成新的 VASP 任务，再把收敛结果回流到数据库。',
    metrics: ['候选优先级', '新增标签', '覆盖提升']
  }
]

const modelStages = [
  { step: '01', title: '数据选择', text: '按元素、原子数、泛函、基组、目标性质和质量标签筛选训练集合。' },
  { step: '02', title: '标签校验', text: '统一能量、力、坐标、单位和计算方法，标记缺失字段与异常记录。' },
  { step: '03', title: '模型训练', text: '对接图神经网络、机器学习势函数和性质回归模型的训练任务。' },
  { step: '04', title: '验证发布', text: '展示测试集误差、适用域、不确定性，并沉淀可复现实验版本。' }
]

const qualityChecks = [
  '训练/验证/测试集划分可追溯',
  '同一模型只混用兼容的理论层级',
  '结构、能量、力和单位明确',
  '异常能量、重复结构和缺失标签可标记',
  '模型版本、参数和数据版本绑定'
]

const workflowStages = [
  {
    step: '01',
    title: '任务登记',
    status: '已上线',
    text: '登记结构来源、目标性质、计算方法、INCAR/KPOINTS/POTCAR 模板和任务优先级。'
  },
  {
    step: '02',
    title: 'VASP 计算',
    status: '待接入',
    text: '对接 Linux/GPU/CPU 队列，记录提交脚本、软件版本、节点信息、收敛状态和运行日志。'
  },
  {
    step: '03',
    title: '结果解析',
    status: '部分可用',
    text: '从 vasprun.xml、OUTCAR、CONTCAR、OSZICAR 中解析结构、能量、力、带隙和元数据。'
  },
  {
    step: '04',
    title: '质量校验',
    status: '可展示',
    text: '检查收敛、单位、异常能量、重复结构、缺失字段和理论层级一致性。'
  },
  {
    step: '05',
    title: '标准入库',
    status: '已上线',
    text: '写入统一展示库，生成数据集目录、记录列表、详情页和可下载文件。'
  },
  {
    step: '06',
    title: '模型回流',
    status: '实验中',
    text: '把合格数据推送到模型页，用于训练计划、快速基线和后续主动学习候选。'
  }
]

const workflowQueues = [
  { name: '结构优化', state: 'ready', count: 128, note: '等待真实 VASP 队列接入' },
  { name: '静态能量', state: 'running', count: 36, note: '当前为演示状态' },
  { name: '频率校正', state: 'pending', count: 14, note: '适合 OpenPoly 类数据' },
  { name: '结果解析', state: 'done', count: 167911, note: '来自当前 H2 展示库' }
]

const workflowArtifacts = [
  'POSCAR / CONTCAR 结构文件',
  'INCAR / KPOINTS / POTCAR 参数',
  'vasprun.xml / OUTCAR 原始输出',
  '能量、力、带隙、HOMO/LUMO 等标签',
  '计算软件、泛函、基组/赝势、数据 DOI',
  '质量标签、版本号、数据来源链接'
]

const workflowRules = [
  { title: '收敛状态', text: '必须记录电子步和离子步是否收敛，未收敛任务不能直接进入金标数据。' },
  { title: '单位统一', text: '能量、力、距离、温度等字段需要统一单位并在详情页明确展示。' },
  { title: '理论层级', text: '同一训练集或对比表必须标注泛函、基组/赝势和软件版本。' },
  { title: '异常检测', text: '原子数异常、能量离群、重复结构、缺坐标等记录应自动打标。' }
]

const workflowMetrics = computed(() => [
  { label: '已入库记录', value: datasets.value.reduce((sum, item) => sum + Number(item.totalConformers || 0), 0), unit: 'records' },
  { label: '数据集数量', value: datasets.value.length, unit: 'datasets' },
  { label: '最高覆盖元素', value: elementEntries.value[0]?.symbol || '-', unit: 'element' },
  { label: '可展示流程', value: workflowStages.length, unit: 'stages' }
])

const modelPlannerTasks = [
  { id: 'potential', label: '机器学习势函数', description: '优先选择含三维结构、能量和力的数据集，适合训练分子/反应体系快速势函数。', target: 'energy_force' },
  { id: 'property', label: '性质预测模型', description: '按目标性质和元素体系选择数据集，适合做 band gap、形成能、介电等标签预测。', target: 'band_gap' },
  { id: 'active_learning', label: '主动学习候选池', description: '组合高覆盖数据与缺口数据，用于生成下一批 VASP 计算候选。', target: 'uncertainty' }
]

const modelTargets = [
  { id: 'energy_force', label: '能量 + 力', exploreProperty: '力' },
  { id: 'energy', label: '总能量', exploreProperty: '能量' },
  { id: 'band_gap', label: 'Band gap', exploreProperty: 'Band gap' },
  { id: 'homo_lumo', label: 'HOMO / LUMO', exploreProperty: 'HOMO/LUMO' },
  { id: 'formation_energy', label: '形成能', exploreProperty: '形成能' },
  { id: 'dielectric', label: '介电性质', exploreProperty: '介电常数' },
  { id: 'reaction', label: '反应路径', exploreProperty: '反应路径' },
  { id: 'uncertainty', label: '不确定性采样', exploreProperty: '能量' }
]

const selectedModelTask = ref('potential')
const targetProperty = ref('energy_force')
const requiredElement = ref('')
const modelAtomLimit = ref('')
const requireForces = ref(true)
const requireStructure = ref(true)
const validationRatio = ref(10)
const testRatio = ref(10)
const splitSeed = ref(2026)
const selectedModelDatasetIds = ref([])
const modelPlanNotice = ref('')
const modelSampleLimit = ref(80)
const trainingRunning = ref(false)
const trainingLogs = ref([])
const trainingResult = ref(null)

const modelDashboardStats = computed(() => [
  { label: '候选数据集', value: datasets.value.length || 0, note: '已接入目录' },
  { label: '结构/构象规模', value: formatCompact(totalConformers.value), note: '可作为训练来源' },
  { label: '高频元素', value: elementEntries.value.length, note: '可用于适用域统计' },
  { label: '模型任务', value: modelTasks.length, note: '当前规划类型' }
])

const currentPlannerTask = computed(() => modelPlannerTasks.find(item => item.id === selectedModelTask.value) || modelPlannerTasks[0])
const currentTarget = computed(() => modelTargets.find(item => item.id === targetProperty.value) || modelTargets[0])

const modelElementOptions = computed(() => {
  const values = new Set()
  datasets.value.forEach(item => (item.elements || []).forEach(element => values.add(element)))
  return Array.from(values).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const trainRatio = computed(() => Math.max(0, 100 - Number(validationRatio.value || 0) - Number(testRatio.value || 0)))

const modelCandidateRows = computed(() => datasets.value
  .map(item => modelCandidateFor(item))
  .filter(row => row.visible)
  .sort((a, b) => b.score - a.score || b.item.totalConformers - a.item.totalConformers))

const modelDatasetRows = computed(() => datasets.value.slice(0, 6).map(item => ({
  id: item.id,
  name: item.name,
  task: modelTaskFor(item),
  method: item.functional || '待确认',
  scale: item.scale,
  readiness: modelReadinessFor(item)
})))

const chosenModelDatasets = computed(() => {
  const selected = modelCandidateRows.value.filter(row => selectedModelDatasetIds.value.includes(row.item.id))
  return selected.length ? selected : modelCandidateRows.value.slice(0, 3)
})

const modelTrainingPlan = computed(() => ({
  planName: `${currentPlannerTask.value.label} - ${currentTarget.value.label}`,
  taskType: selectedModelTask.value,
  targetProperty: currentTarget.value.label,
  filters: {
    requiredElement: requiredElement.value || '不限',
    atomLimit: modelAtomLimit.value || '不限',
    requireStructure: requireStructure.value,
    requireForces: requireForces.value
  },
  datasetIds: chosenModelDatasets.value.map(row => row.item.id),
  datasets: chosenModelDatasets.value.map(row => ({
    id: row.item.id,
    name: row.item.name,
    score: row.score,
    scale: row.item.scale,
    functional: row.item.functional,
    basisSet: row.item.basisSet,
    reasons: row.reasons,
    risks: row.risks
  })),
  split: {
    train: `${trainRatio.value}%`,
    validation: `${validationRatio.value}%`,
    test: `${testRatio.value}%`,
    seed: splitSeed.value
  },
  qualityGates: qualityChecks,
  nextSteps: [
    '确认目标性质单位和标签字段',
    '导出训练/验证/测试划分清单',
    '接入真实训练脚本或远程 GPU 队列',
    '记录模型版本、数据版本与评估指标'
  ]
}))

const qualityHighlights = computed(() => {
  if (!quality.value) return []
  const highRisk = quality.value.datasets.filter(item => item.level === '高风险' || item.score < 55).length
  const modelReady = quality.value.datasets.filter(item => item.score >= 80).length
  const passed = quality.value.datasets.filter(item => item.reviewStatus === '通过发布').length
  return [
    { label: '平均质量分', value: quality.value.averageScore, note: '0-100 综合评分' },
    { label: '已验证数据集', value: quality.value.totalDatasets, note: `${formatCompact(quality.value.totalRecords)} 条记录` },
    { label: '建模优先集', value: modelReady, note: '评分达到 80 分以上' },
    { label: '通过发布', value: passed, note: `${quality.value.datasets.length - passed} 个数据集需复核` },
    { label: '高风险项', value: highRisk, note: `${quality.value.issues.length} 条待处理问题` }
  ]
})

const qualityDatasetRows = computed(() => quality.value?.datasets || [])
const qualityIssueRows = computed(() => (quality.value?.issues || []).slice(0, 12))
const auditStageRows = computed(() => quality.value?.auditStages || [])
const auditRuleRows = computed(() => quality.value?.auditRules || [])
const auditLedgerRows = computed(() => {
  const rows = qualityDatasetRows.value
  const needsReview = rows.filter(row => row.reviewStatus !== '通过发布')
  return (needsReview.length ? needsReview : rows).slice(0, 8)
})
const auditStatusSummary = computed(() => {
  const counts = new Map()
  qualityDatasetRows.value.forEach(row => {
    counts.set(row.reviewStatus, (counts.get(row.reviewStatus) || 0) + 1)
  })
  return Array.from(counts.entries()).map(([label, value]) => ({ label, value }))
})
const publicationSummary = computed(() => {
  const published = publicationRows.value.filter(item => item.published).length
  return {
    published,
    hidden: publicationRows.value.length - published,
    total: publicationRows.value.length
  }
})

function openDataset(item) {
  if (!item.linkable) return
  router.push({ name: 'dataset-records', params: { id: item.id } })
}

function openExplore() {
  router.push({ name: 'explore' })
}

function openAssistant() {
  router.push({ name: 'assistant' })
}

function openModelExplore() {
  const query = {}
  if (currentTarget.value.exploreProperty) query.properties = currentTarget.value.exploreProperty
  if (requiredElement.value) query.elements = requiredElement.value
  if (modelAtomLimit.value !== '') query.atomMax = modelAtomLimit.value
  router.push({ name: 'explore', query })
}

function normalizeTab(value) {
  return ['quality', 'model', 'workflow'].includes(value) ? value : 'data'
}

function openTab(tab) {
  activeTab.value = normalizeTab(tab)
  router.replace({
    name: 'home',
    query: activeTab.value === 'data' ? {} : { tab: activeTab.value }
  })
}

watch(() => route.query.tab, value => {
  activeTab.value = normalizeTab(value)
})

watch(activeTab, value => {
  if (value === 'quality') {
    loadQuality()
  }
})

function metaFor(id) {
  return datasetMeta[id] || { code: 'DFT', kind: '计算数据', accent: '#2f6fed' }
}

function formatCompact(value) {
  const number = Number(value || 0)
  if (number >= 1000000) return `${(number / 1000000).toFixed(1)}M`
  if (number >= 1000) return `${(number / 1000).toFixed(1)}K`
  return number.toLocaleString('zh-CN')
}

function formatPercent(value) {
  return `${Math.round(Number(value || 0) * 100)}%`
}

function qualityAccent(score) {
  const number = Number(score || 0)
  if (number >= 85) return '#2f8f6b'
  if (number >= 70) return '#4d7fd6'
  if (number >= 55) return '#b1843f'
  return '#cf5f5f'
}

function qualityClass(value) {
  if (value === '高' || value === '高风险' || value === '阻断' || value === '暂缓发布') return 'risk-high'
  if (value === '中' || value === '需补充' || value === '复核' || value === '专家复核') return 'risk-mid'
  if (value === '低' || value === '观察' || value === '不适用') return 'risk-low'
  return 'risk-ok'
}

function metricByKey(row, key) {
  return row.metrics.find(item => item.key === key) || { ratio: 0, filled: 0, total: row.totalRecords, expected: false }
}

function formatQualityTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN')
}

function shortElements(elements = []) {
  return elements.slice(0, 7)
}

function selectModelTask(taskId) {
  selectedModelTask.value = taskId
  const task = modelPlannerTasks.find(item => item.id === taskId)
  if (task) targetProperty.value = task.target
  requireForces.value = taskId === 'potential'
  selectedModelDatasetIds.value = []
  modelPlanNotice.value = ''
}

function toggleModelDataset(id) {
  selectedModelDatasetIds.value = selectedModelDatasetIds.value.includes(id)
    ? selectedModelDatasetIds.value.filter(item => item !== id)
    : [...selectedModelDatasetIds.value, id]
  modelPlanNotice.value = ''
}

function resetModelPlanner() {
  selectedModelTask.value = 'potential'
  targetProperty.value = 'energy_force'
  requiredElement.value = ''
  modelAtomLimit.value = ''
  requireForces.value = true
  requireStructure.value = true
  validationRatio.value = 10
  testRatio.value = 10
  splitSeed.value = 2026
  modelSampleLimit.value = 80
  selectedModelDatasetIds.value = []
  modelPlanNotice.value = ''
  trainingLogs.value = []
  trainingResult.value = null
}

function exportModelPlan() {
  const payload = JSON.stringify(modelTrainingPlan.value, null, 2)
  const blob = new Blob([payload], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `vasp-show-model-plan-${selectedModelTask.value}-${Date.now()}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  modelPlanNotice.value = '训练计划 JSON 已导出，可交给训练脚本或后续 GPU 队列使用。'
}

async function runQuickBaseline() {
  trainingRunning.value = true
  trainingResult.value = null
  trainingLogs.value = []
  const selectedRows = chosenModelDatasets.value.slice(0, 5)
  logTraining(`开始读取 ${selectedRows.length} 个数据集的记录样本。`)
  try {
    const samples = []
    for (const row of selectedRows) {
      const limit = Math.max(12, Math.min(80, Number(modelSampleLimit.value) || 80))
      logTraining(`读取 ${row.item.name}，最多 ${limit} 条记录。`)
      const page = await fetchDatasetRecords(row.item.id, { limit })
      const usable = page.records
        .map(record => sampleFromRecord(record, row.item.id))
        .filter(Boolean)
      samples.push(...usable)
      logTraining(`${row.item.id}: 可用于能量回归的记录 ${usable.length} 条。`)
    }
    if (samples.length < 12) {
      logTraining('可用样本不足 12 条，无法形成稳定训练/测试划分。')
      return
    }

    const shuffled = seededShuffle(samples, Number(splitSeed.value) || 2026)
    const testSize = Math.max(2, Math.floor(shuffled.length * Number(testRatio.value || 10) / 100))
    const valSize = Math.max(1, Math.floor(shuffled.length * Number(validationRatio.value || 10) / 100))
    const test = shuffled.slice(0, testSize)
    const validation = shuffled.slice(testSize, testSize + valSize)
    const train = shuffled.slice(testSize + valSize)
    if (train.length < 6) {
      logTraining('训练样本过少，请降低验证/测试比例或增加采样数。')
      return
    }

    logTraining(`划分完成：训练 ${train.length} / 验证 ${validation.length} / 测试 ${test.length}。`)
    const featureNames = buildFeatureNames(samples)
    const model = fitRidgeRegression(train, featureNames)
    const trainMetrics = evaluateRegression(model, train)
    const validationMetrics = evaluateRegression(model, validation)
    const testMetrics = evaluateRegression(model, test)
    trainingResult.value = {
      type: '前端临时岭回归基线',
      target: 'energy',
      generatedAt: new Date().toLocaleString('zh-CN'),
      sampleCount: samples.length,
      datasetCount: selectedRows.length,
      split: { train: train.length, validation: validation.length, test: test.length },
      metrics: { train: trainMetrics, validation: validationMetrics, test: testMetrics },
      topFeatures: topModelFeatures(model, 8),
      warning: '该结果基于页面摘要字段和少量采样记录，只在浏览器内用 JavaScript 做临时基线评估，用于演示数据到模型的闭环和发现数据质量问题；不等同于真实深度势函数或正式材料性质模型。'
    }
    logTraining(`基线评估完成：测试 MAE=${formatMetric(testMetrics.mae)}，RMSE=${formatMetric(testMetrics.rmse)}，R2=${formatMetric(testMetrics.r2)}。`)
  } catch (err) {
    logTraining(`基线评估失败：${err.message || err}`)
  } finally {
    trainingRunning.value = false
  }
}

function exportTrainingResult() {
  if (!trainingResult.value) return
  const payload = JSON.stringify({ plan: modelTrainingPlan.value, result: trainingResult.value }, null, 2)
  const blob = new Blob([payload], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `vasp-show-training-result-${Date.now()}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function logTraining(message) {
  trainingLogs.value = [...trainingLogs.value, `${new Date().toLocaleTimeString('zh-CN')}  ${message}`]
}

function sampleFromRecord(record, datasetId) {
  const y = parseNumeric(record.energy)
  const atomCount = parseNumeric(record.atomCount)
  if (!Number.isFinite(y) || !Number.isFinite(atomCount)) return null
  return {
    y,
    datasetId,
    atomCount,
    composition: record.composition || record.smiles || '',
    elements: parseCompositionCounts(record.composition || record.smiles || '')
  }
}

function buildFeatureNames(samples) {
  const elementNames = Array.from(new Set(samples.flatMap(sample => Object.keys(sample.elements))))
    .filter(symbol => symbol.length <= 2)
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
    .slice(0, 12)
  const datasetNames = Array.from(new Set(samples.map(sample => sample.datasetId))).sort()
  return ['atomCount', ...elementNames.map(symbol => `element:${symbol}`), ...datasetNames.slice(1).map(id => `dataset:${id}`)]
}

function fitRidgeRegression(train, featureNames) {
  const raw = train.map(sample => featureVector(sample, featureNames))
  const stats = featureStats(raw)
  const x = raw.map(row => [1, ...row.map((value, index) => normalizeFeature(value, stats[index]))])
  const y = train.map(sample => sample.y)
  const lambda = 1e-6
  const xtx = Array.from({ length: x[0].length }, () => Array(x[0].length).fill(0))
  const xty = Array(x[0].length).fill(0)
  x.forEach((row, i) => {
    row.forEach((value, a) => {
      xty[a] += value * y[i]
      row.forEach((other, b) => {
        xtx[a][b] += value * other
      })
    })
  })
  for (let i = 1; i < xtx.length; i += 1) xtx[i][i] += lambda
  const coefficients = solveLinearSystem(xtx, xty)
  return { coefficients, featureNames, stats }
}

function evaluateRegression(model, rows) {
  if (!rows.length) return { mae: 0, rmse: 0, r2: 0 }
  const truth = rows.map(row => row.y)
  const pred = rows.map(row => predict(model, row))
  const mean = truth.reduce((sum, value) => sum + value, 0) / truth.length
  const errors = truth.map((value, index) => pred[index] - value)
  const mae = errors.reduce((sum, value) => sum + Math.abs(value), 0) / errors.length
  const rmse = Math.sqrt(errors.reduce((sum, value) => sum + value * value, 0) / errors.length)
  const ssRes = errors.reduce((sum, value) => sum + value * value, 0)
  const ssTot = truth.reduce((sum, value) => sum + (value - mean) ** 2, 0)
  return { mae, rmse, r2: ssTot === 0 ? 0 : 1 - ssRes / ssTot }
}

function predict(model, sample) {
  const raw = featureVector(sample, model.featureNames)
  return model.coefficients[0] + raw.reduce((sum, value, index) => {
    return sum + normalizeFeature(value, model.stats[index]) * model.coefficients[index + 1]
  }, 0)
}

function featureVector(sample, featureNames) {
  return featureNames.map(name => {
    if (name === 'atomCount') return sample.atomCount
    if (name.startsWith('element:')) return sample.elements[name.slice(8)] || 0
    if (name.startsWith('dataset:')) return sample.datasetId === name.slice(8) ? 1 : 0
    return 0
  })
}

function featureStats(rows) {
  return rows[0].map((_, index) => {
    const values = rows.map(row => row[index])
    const mean = values.reduce((sum, value) => sum + value, 0) / values.length
    const variance = values.reduce((sum, value) => sum + (value - mean) ** 2, 0) / values.length
    return { mean, std: Math.sqrt(variance) || 1 }
  })
}

function normalizeFeature(value, stat) {
  return (value - stat.mean) / stat.std
}

function solveLinearSystem(matrix, vector) {
  const n = vector.length
  const a = matrix.map((row, index) => [...row, vector[index]])
  for (let col = 0; col < n; col += 1) {
    let pivot = col
    for (let row = col + 1; row < n; row += 1) {
      if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) pivot = row
    }
    ;[a[col], a[pivot]] = [a[pivot], a[col]]
    const divisor = a[col][col] || 1e-12
    for (let j = col; j <= n; j += 1) a[col][j] /= divisor
    for (let row = 0; row < n; row += 1) {
      if (row === col) continue
      const factor = a[row][col]
      for (let j = col; j <= n; j += 1) a[row][j] -= factor * a[col][j]
    }
  }
  return a.map(row => row[n])
}

function topModelFeatures(model, limit) {
  return model.featureNames
    .map((name, index) => ({ name, weight: model.coefficients[index + 1] }))
    .sort((a, b) => Math.abs(b.weight) - Math.abs(a.weight))
    .slice(0, limit)
}

function seededShuffle(values, seed) {
  const output = [...values]
  let state = seed % 2147483647
  if (state <= 0) state += 2147483646
  for (let i = output.length - 1; i > 0; i -= 1) {
    state = state * 16807 % 2147483647
    const j = state % (i + 1)
    ;[output[i], output[j]] = [output[j], output[i]]
  }
  return output
}

function parseCompositionCounts(value) {
  const counts = {}
  const pattern = /([A-Z][a-z]?)(\d*\.?\d*)/g
  let match
  while ((match = pattern.exec(String(value || ''))) !== null) {
    counts[match[1]] = (counts[match[1]] || 0) + (Number(match[2]) || 1)
  }
  return counts
}

function parseNumeric(value) {
  const match = String(value ?? '').replace(/,/g, '').match(/-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/)
  return match ? Number(match[0]) : Number.NaN
}

function formatMetric(value) {
  if (!Number.isFinite(value)) return '-'
  return Math.abs(value) >= 100 ? value.toFixed(2) : value.toPrecision(4)
}

function modelCandidateFor(item) {
  const capabilities = datasetCapabilitiesFor(item.id)
  const minAtoms = parseAtomRange(item.atomCountRange).min
  const maxAtoms = parseAtomRange(item.atomCountRange).max
  const reasons = []
  const risks = []
  let score = 35
  let visible = true

  if (requiredElement.value && !(item.elements || []).includes(requiredElement.value)) visible = false
  if (modelAtomLimit.value !== '' && minAtoms > Number(modelAtomLimit.value)) visible = false
  if (requireForces.value && !capabilities.includes('force')) visible = false
  if (requireStructure.value && !capabilities.includes('structure')) visible = false

  if (capabilities.includes(targetProperty.value)) {
    score += 22
    reasons.push(`含${currentTarget.value.label}标签`)
  } else if (targetProperty.value === 'energy_force' && capabilities.includes('energy') && capabilities.includes('force')) {
    score += 24
    reasons.push('能量与力标签完整')
  } else {
    risks.push(`目标性质 ${currentTarget.value.label} 需进一步校验`)
  }

  if (selectedModelTask.value === 'potential') {
    if (capabilities.includes('force')) score += 18
    if (capabilities.includes('structure')) score += 12
    if (!capabilities.includes('force')) risks.push('缺少力标签，不适合作为势函数主训练集')
  }
  if (selectedModelTask.value === 'property') {
    if (capabilities.includes('band_gap') || capabilities.includes('homo_lumo') || capabilities.includes('formation_energy') || capabilities.includes('dielectric')) score += 16
  }
  if (selectedModelTask.value === 'active_learning') {
    if (Number(item.totalConformers || 0) > 100000) score += 16
    reasons.push('可作为候选覆盖池')
  }

  if (Number(item.totalConformers || 0) > 1000000) {
    score += 14
    reasons.push('规模大，适合训练/验证划分')
  } else if (Number(item.totalConformers || 0) < 5000) {
    risks.push('规模偏小，更适合作为补充或测试集')
  }

  if (maxAtoms > 300) risks.push('原子数跨度较大，建议按尺寸分桶训练')
  if (item.id === 'openpoly_calculated') risks.push('无三维坐标，不能直接训练结构模型')

  return {
    item,
    visible,
    score: Math.max(0, Math.min(100, score)),
    scoreLabel: `${Math.max(0, Math.min(100, score))}%`,
    capabilities,
    reasons: reasons.length ? reasons : ['可作为候选数据源'],
    risks: risks.length ? risks : ['暂无明显阻断项']
  }
}

function datasetCapabilitiesFor(id) {
  const map = {
    ani_gdb_s03: ['structure', 'energy'],
    data0000_aselmdb: ['structure', 'energy', 'force', 'homo_lumo'],
    openpoly_calculated: ['energy', 'homo_lumo', 'dielectric'],
    ani1x_less_is_more: ['structure', 'energy', 'force', 'energy_force'],
    transition1x: ['structure', 'energy', 'force', 'energy_force', 'reaction'],
    twod_matpedia: ['structure', 'energy', 'band_gap'],
    jarvis_dft_3d: ['structure', 'energy', 'band_gap', 'formation_energy'],
    jarvis_dft_2d: ['structure', 'energy', 'band_gap', 'formation_energy'],
    polymer_genome_1073: ['structure', 'energy', 'band_gap', 'dielectric'],
    qmof_database: ['structure', 'energy', 'band_gap'],
    matbench_wbm_summary: ['energy', 'band_gap', 'formation_energy', 'stability'],
    matbench_mp_energies: ['energy', 'formation_energy', 'stability'],
    matbench_phonondb_pbe_103: ['structure', 'thermal', 'phonon'],
    hydrocarbons_gap_ch: ['structure', 'energy', 'force', 'energy_force'],
    matbench_v01_dielectric: ['structure', 'dielectric'],
    matbench_v01_jdft2d: ['structure', 'energy', 'formation_energy'],
    matbench_v01_phonons: ['structure', 'thermal', 'phonon'],
    matbench_v01_perovskites: ['structure', 'energy', 'formation_energy'],
    matbench_v01_log_gvrh: ['structure', 'mechanical'],
    matbench_v01_log_kvrh: ['structure', 'mechanical'],
    qm9_molecular_dft: ['energy', 'homo_lumo']
  }
  return map[id] || ['structure', 'energy']
}

function parseAtomRange(value) {
  const match = String(value || '').match(/(\d+)\s*-\s*(\d+)/)
  return match ? { min: Number(match[1]), max: Number(match[2]) } : { min: 0, max: 0 }
}

function modelTaskFor(item) {
  if (item.id === 'transition1x') return '反应路径势函数'
  if (item.id === 'ani1x_less_is_more' || item.id === 'ani_gdb_s03' || item.id === 'hydrocarbons_gap_ch') return '分子/反应势函数'
  if (item.id === 'qm9_molecular_dft') return '小分子性质预测'
  if (item.id === 'openpoly_calculated' || item.id === 'polymer_genome_1073') return '聚合物性质预测'
  if (item.id?.includes('jarvis') || item.id === 'twod_matpedia' || item.id?.startsWith('matbench_')) return '晶体/二维材料性质预测'
  if (item.id === 'qmof_database') return '多孔材料性质预测'
  return '性质预测'
}

function modelReadinessFor(item) {
  if (item.id === 'ani1x_less_is_more' || item.id === 'transition1x' || item.id === 'hydrocarbons_gap_ch') return '高：含能量与力'
  if (item.id === 'matbench_wbm_summary' || item.id === 'matbench_mp_energies') return '中：适合稳定性/能量基准'
  if (item.id === 'matbench_phonondb_pbe_103' || item.id === 'matbench_v01_phonons') return '中：小规模声子/热输运示例'
  if (item.id?.startsWith('matbench_v01_')) return '中：结构-性质基准任务'
  if (item.id === 'qm9_molecular_dft') return '中：表格版分子性质基准'
  if (item.id === 'ani_gdb_s03' || item.id === 'data0000_aselmdb') return '中：适合标签训练'
  if (item.id === 'openpoly_calculated') return '中：缺三维结构'
  return '待校验：需补质量标签'
}
</script>

<template>
  <div class="page">
    <AppTopbar @brand-click="openTab('data')">
      <nav class="main-nav">
        <button :class="{ active: activeTab === 'data' }" @click="openTab('data')">数据中心</button>
        <button @click="openExplore">数据发现</button>
        <button :class="{ active: activeTab === 'quality' }" @click="openTab('quality')">质量验证</button>
        <button @click="openAssistant">智能助手</button>
        <button :class="{ active: activeTab === 'model' }" @click="openTab('model')">模型</button>
        <button :class="{ active: activeTab === 'workflow' }" @click="openTab('workflow')">工作流</button>
      </nav>
    </AppTopbar>

    <main class="shell">
      <section v-if="activeTab === 'data'" class="dashboard-hero">
        <div class="dashboard-copy">
          <p class="eyebrow">VASP / PAW + DFT · MATERIALS COMPUTATION DATABASE</p>
          <h1>计算材料数据平台 · 可信数据节点</h1>
          <p>
            面向高分子、分子、晶体、二维材料、反应路径和 MOF 的计算数据资源节点，组织 VASP/DFT 任务、结构、能量、力、性质标签与质量证据链。
          </p>
          <div class="dashboard-tags">
            <span>VASP workflow</span>
            <span>PAW + DFT</span>
            <span>Traceable data</span>
            <span>Model-ready</span>
          </div>
        </div>

        <div class="dashboard-scene-panel" aria-label="计算材料三维场景：聚合物长链与电子轨道">
          <Hero3DScene />
          <div class="scene-overlay">
            <span>polymer chain</span>
            <span>electron orbital</span>
            <span>particle flow</span>
          </div>
        </div>

        <div class="dashboard-kpis">
          <KpiCard
            v-for="item in heroKpis"
            :key="item.label"
            :label="item.label"
            :value="item.value"
            :note="item.note"
          />
        </div>
      </section>

      <section v-if="false" class="review-section">
        <div class="review-header">
          <div>
            <p class="eyebrow">Node review mode</p>
            <h2>先回答专家问题，再展示数据库功能</h2>
            <p>调度会展示应从“页面能查什么”升级为“节点能稳定生产什么可信数据、如何进入平台、能支撑什么应用”。</p>
          </div>
          <button type="button" @click="openExplore">进入数据发现</button>
        </div>

        <div class="concern-grid">
          <article v-for="item in expertConcerns" :key="item.title" class="concern-card">
            <span>{{ item.title }}</span>
            <p>{{ item.text }}</p>
          </article>
        </div>

        <div class="evidence-panel">
          <div class="panel-title">
            <span>VASP 数据证据链</span>
            <strong>一条记录必须能从性质值追溯回原始计算</strong>
          </div>
          <div class="evidence-flow">
            <article v-for="item in evidenceSteps" :key="item.step" class="evidence-step">
              <i>{{ item.step }}</i>
              <strong>{{ item.title }}</strong>
              <p>{{ item.text }}</p>
            </article>
          </div>
        </div>

        <div class="demo-grid">
          <article class="demo-card">
            <span>建议现场演示</span>
            <h3>固态电解质候选聚合物筛选</h3>
            <p>用“Li、O、F、Band gap、结合能/迁移能垒”等条件筛选结构，打开一条记录展示结构、参数、性质和元数据，再说明缺失性质如何回流计算。</p>
          </article>
          <article v-for="item in demoScenes" :key="item.title" class="demo-card compact">
            <span>{{ item.title }}</span>
            <p>{{ item.text }}</p>
          </article>
        </div>
      </section>

      <section v-if="activeTab === 'data'" class="dashboard-overview">
        <button class="explorer-card" @click="openExplore">
          <div class="search-symbol"></div>
          <div>
            <span>数据浏览</span>
            <strong>按数据集、元素、泛函、结构名称或 DOI 检索</strong>
          </div>
        </button>
        <CoveragePanel :categories="coverageCards" />
        <ElementSpectrum :elements="elementEntries" />
      </section>

      <section v-show="activeTab === 'data'" class="section">
        <div class="section-head">
          <div>
            <h2>已接入数据集</h2>
            <p>选择数据集进入记录列表，查看结构、能量和计算属性。</p>
          </div>
        </div>

        <div v-if="loading" class="state">正在读取数据集...</div>
        <div v-else-if="error" class="state error">{{ error }}</div>
        <div v-else class="dataset-grid">
          <article
            v-for="item in datasets"
            :key="item.id"
            class="dataset-card"
            :style="{ '--accent': metaFor(item.id).accent }"
            @click="openDataset(item)"
          >
            <div class="card-top">
              <div class="dataset-mark">
                <span>{{ metaFor(item.id).code }}</span>
              </div>
              <div class="card-title">
                <h3>{{ item.name }}</h3>
                <p>{{ item.id }}</p>
              </div>
              <span class="pill">{{ metaFor(item.id).kind }}</span>
            </div>
            <p class="intro">{{ item.intro }}</p>

            <div class="element-chips">
              <span v-for="element in shortElements(item.elements)" :key="element">{{ element }}</span>
              <span v-if="item.elements.length > 7">+{{ item.elements.length - 7 }}</span>
            </div>

            <div class="stats">
              <div>
                <span>规模</span>
                <strong>{{ item.scale }}</strong>
              </div>
              <div>
                <span>原子数</span>
                <strong>{{ item.atomCountRange }}</strong>
              </div>
              <div>
                <span>方法</span>
                <strong>{{ item.functional }}</strong>
              </div>
              <div>
                <span>基组/赝势</span>
                <strong>{{ item.basisSet }}</strong>
              </div>
            </div>
            <div class="dataset-card-action">
              <span>查看记录</span>
              <strong>进入数据集</strong>
            </div>
          </article>
        </div>
      </section>

      <section v-show="activeTab === 'quality'" class="quality-section">
        <div class="quality-hero">
          <div>
            <p class="eyebrow">Data quality validation</p>
            <h2>数据校准与质量验证</h2>
            <p>
              对统一入库后的 display_records 做字段完整性、结构可用性、数值标签、来源追溯和建模可用性检查。
              当前结果用于专家评审和数据治理，后续可继续接入原始 VASP/ORCA 输出文件级校验。
            </p>
          </div>
          <div class="quality-actions">
            <button type="button" @click="refreshQuality">重新验证</button>
            <button class="secondary" type="button" @click="openTab('workflow')">查看入库流程</button>
          </div>
        </div>

        <div v-if="qualityLoading" class="state">正在统计全库质量指标...</div>
        <div v-else-if="qualityError" class="state error">{{ qualityError }}</div>
        <template v-else-if="quality">
          <div class="quality-summary">
            <article
              v-for="item in qualityHighlights"
              :key="item.label"
              :style="{ '--accent': item.label === '高风险项' ? '#cf5f5f' : '#2f6fed' }"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.note }}</small>
            </article>
          </div>

          <div class="quality-scope">
            <strong>验证范围</strong>
            <span>{{ quality.scope }}</span>
            <em>生成时间：{{ formatQualityTime(quality.generatedAt) }}</em>
          </div>

          <section class="preflight-panel">
            <div class="section-head compact">
              <div>
                <h2>页面内数据预检</h2>
                <p>注册用户和管理员可在这里验证 DOI/下载链接，上传抽样文件识别字段，生成评分后提交超级管理员审核。</p>
              </div>
              <button type="button" @click="submitPreflightForReview" :disabled="intakeSubmitting">
                {{ intakeSubmitting ? '提交中...' : '提交审核申请' }}
              </button>
            </div>

            <p v-if="preflightError" class="feedback error">{{ preflightError }}</p>
            <p v-if="preflightNotice" class="feedback success">{{ preflightNotice }}</p>

            <div v-if="!isAuthenticated" class="preflight-login">
              <strong>登录后可进行在线预检</strong>
              <span>游客可查看质量结果；注册用户可验证链接、上传抽样文件并提交审核。</span>
              <button type="button" @click="router.push({ name: 'login', query: { redirect: '/?tab=quality' } })">登录 / 注册</button>
            </div>

            <div v-else class="preflight-grid">
              <form class="link-checker" @submit.prevent="runLinkValidation">
                <h3>链接真实性验证</h3>
                <label>
                  <span>DOI</span>
                  <input v-model="linkForm.doi" placeholder="10.xxxx/xxxxx 或 https://doi.org/...">
                </label>
                <label>
                  <span>论文链接</span>
                  <input v-model="linkForm.paperUrl" placeholder="https://...">
                </label>
                <label>
                  <span>数据下载链接</span>
                  <input v-model="linkForm.dataUrl" placeholder="https://...">
                </label>
                <button type="submit" :disabled="linkChecking">{{ linkChecking ? '验证中...' : '验证链接' }}</button>

                <div v-if="linkResult" class="link-result">
                  <strong>{{ linkResult.score }} 分</strong>
                  <article v-for="check in linkResult.checks" :key="check.key" :class="{ ok: check.reachable }">
                    <span>{{ check.reachable ? '通过' : '失败' }}</span>
                    <div>
                      <b>{{ check.label }}</b>
                      <small>{{ check.statusCode || '-' }} · {{ check.message }}</small>
                      <em>{{ check.finalUrl || check.url }}</em>
                    </div>
                  </article>
                </div>
              </form>

              <section class="file-previewer">
                <h3>文件字段预检</h3>
                <label class="file-drop">
                  <input type="file" accept=".csv,.tsv,.txt,.json,.jsonl,.ndjson,.xyz,.extxyz,.cif,.h5,.hdf5,.gz" @change="handlePreviewFile">
                  <strong>{{ fileChecking ? '正在解析...' : '上传抽样文件' }}</strong>
                  <span>支持 CSV/TSV、JSON/JSONL、XYZ、CIF、HDF5/H5；预检文件不超过 30MB。</span>
                </label>

                <div v-if="filePreview" class="file-preview-result">
                  <div class="preview-score">
                    <strong>{{ filePreview.score }}</strong>
                    <span>{{ filePreview.format }}</span>
                    <small>{{ filePreview.summary }}</small>
                  </div>
                  <div class="missing-list">
                    <b>缺失字段</b>
                    <span v-for="field in filePreview.missingFields" :key="field">{{ field }}</span>
                  </div>
                  <div v-if="filePreview.recommendations?.length" class="preflight-recommendations">
                    <b>预检建议</b>
                    <p v-for="item in filePreview.recommendations" :key="item">{{ item }}</p>
                  </div>
                  <div class="field-table">
                    <div class="field-row head">
                      <span>字段</span>
                      <span>类型</span>
                      <span>映射</span>
                      <span>样例</span>
                    </div>
                    <div v-for="field in filePreview.fields.slice(0, 12)" :key="field.name" class="field-row">
                      <strong>{{ field.name }}</strong>
                      <span>{{ field.type }}</span>
                      <span>{{ field.mappedField || '未识别' }}</span>
                      <small>{{ field.examples.join(' / ') || '-' }}</small>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </section>

          <section v-if="isSuperAdmin" class="publication-panel">
            <div class="section-head compact">
              <div>
                <h2>数据集发布控制</h2>
                <p>超级管理员可以临时隐藏某些数据集；隐藏后普通用户和游客不会在列表、详情和记录接口中看到。</p>
              </div>
              <div class="publication-stat">
                <strong>{{ publicationSummary.published }}</strong>
                <span>已发布 / {{ publicationSummary.total }}</span>
                <small>{{ publicationSummary.hidden }} 个隐藏</small>
              </div>
            </div>
            <label class="publication-note">
              <span>本次调整说明</span>
              <input v-model="publicationNote" placeholder="例如：等待 DOI 核验、字段单位需复核">
            </label>
            <div v-if="publicationLoading" class="state small">正在读取发布状态...</div>
            <div v-else class="publication-list">
              <article v-for="row in publicationRows" :key="row.datasetId" :class="{ hidden: !row.published }">
                <div>
                  <strong>{{ row.datasetName }}</strong>
                  <small>{{ row.datasetId }} · {{ row.note || '无说明' }}</small>
                  <small>Gate: {{ row.grade || 'Silver' }} · {{ row.decision || (row.published ? 'PUBLISH' : 'HIDE') }}</small>
                </div>
                <button type="button" @click="togglePublication(row)">
                  {{ row.published ? '隐藏' : '发布' }}
                </button>
              </article>
            </div>
          </section>

          <section v-if="canViewQualityReview" class="audit-flow-panel">
            <div class="section-head compact">
              <div>
                <h2>审核流程</h2>
                <p>从公开来源、格式适配、字段映射到质量验证和发布回滚，形成可追溯的入库闭环。</p>
              </div>
            </div>
            <div class="audit-flow">
              <article v-for="stage in auditStageRows" :key="stage.key">
                <span>{{ stage.status }}</span>
                <strong>{{ stage.title }}</strong>
                <small>{{ stage.owner }}</small>
                <p>{{ stage.purpose }}</p>
                <em>{{ stage.evidence }}</em>
              </article>
            </div>
          </section>

          <section v-if="canViewQualityReview" class="audit-status-panel">
            <article
              v-for="item in auditStatusSummary"
              :key="item.label"
              :class="qualityClass(item.label)"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <small>数据集</small>
            </article>
          </section>

          <section class="quality-gates">
            <article
              v-for="gate in quality.gates"
              :key="gate.key"
              :class="qualityClass(gate.status)"
              :style="{ '--accent': qualityAccent(gate.score) }"
            >
              <div class="gate-ring">
                <strong>{{ gate.score }}</strong>
                <span>{{ gate.status }}</span>
              </div>
              <div>
                <h3>{{ gate.title }}</h3>
                <p>{{ gate.description }}</p>
              </div>
            </article>
          </section>

          <section v-if="!canViewQualityReview" class="quality-locked-panel">
            <strong>完整质量审核明细已受权限保护</strong>
            <p>游客和注册用户只能查看公开质量摘要、质量门控说明，并可进行链接/文件预检后提交审核申请。数据集级审核台账、待处理问题、质量报告和发布控制仅管理员/超级管理员可见。</p>
          </section>

          <section v-if="canViewQualityReview" class="audit-rule-panel">
            <div class="section-head compact">
              <div>
                <h2>专家审核门控规则</h2>
                <p>规则用于决定数据集是标杆训练集、可展示数据，还是仅允许内部排查。</p>
              </div>
            </div>
            <div class="audit-rule-grid">
              <article v-for="rule in auditRuleRows" :key="rule.key">
                <span>{{ rule.category }} · 权重 {{ rule.weight }}%</span>
                <strong>{{ rule.title }}</strong>
                <p>{{ rule.passCriteria }}</p>
                <em>{{ rule.failureAction }}</em>
              </article>
            </div>
          </section>

          <section v-if="canViewQualityReview" class="quality-table-panel">
            <div class="section-head compact">
              <div>
                <h2>数据集质量明细</h2>
                <p>评分越高代表越适合直接展示、下载和建模；表格型数据不会因缺少三维结构被强制扣分。</p>
              </div>
            </div>
            <div class="quality-table">
              <div class="quality-row head">
                <span>数据集</span>
                <span>评分</span>
                <span>基础</span>
                <span>结构</span>
                <span>数值</span>
                <span>追溯</span>
                <span>审核</span>
                <span>关键覆盖</span>
              </div>
              <button
                v-for="row in qualityDatasetRows"
                :key="row.datasetId"
                class="quality-row"
                type="button"
                :style="{ '--accent': qualityAccent(row.score) }"
                @click="openDataset({ id: row.datasetId, linkable: true })"
              >
                <strong>
                  {{ row.name }}
                  <small>{{ row.type }} · {{ formatCompact(row.totalRecords) }} records</small>
                </strong>
                <span class="score-pill">{{ row.score }} · {{ row.level }}</span>
                <span>{{ formatPercent(row.completenessScore) }}</span>
                <span>{{ formatPercent(row.structureScore) }}</span>
                <span>{{ formatPercent(row.numericScore) }}</span>
                <span>{{ formatPercent(row.traceabilityScore) }}</span>
                <span class="review-pill" :class="qualityClass(row.reviewStatus)">
                  {{ row.reviewStatus }}
                  <small>{{ row.publishTier }}</small>
                </span>
                <span class="metric-strip">
                  <i :style="{ width: formatPercent(metricByKey(row, 'targetLabel').ratio) }"></i>
                  <em>目标标签 {{ formatPercent(metricByKey(row, 'targetLabel').ratio) }}</em>
                  <em>坐标 {{ row.structureExpected ? formatPercent(metricByKey(row, 'structure').ratio) : '表格型' }}</em>
                  <em>力 {{ row.forceExpected ? formatPercent(metricByKey(row, 'forces').ratio) : '非必需' }}</em>
                </span>
              </button>
            </div>
          </section>

          <div v-if="canViewQualityReview" class="quality-grid">
            <section class="audit-ledger-panel">
              <div class="section-head compact">
                <div>
                  <h2>数据集审核台账</h2>
                  <p>优先列出需要专家复核或暂缓发布的数据集；每项门控都来自后端质量统计。</p>
                </div>
              </div>
              <div class="audit-ledger-list">
                <article
                  v-for="row in auditLedgerRows"
                  :key="`audit-${row.datasetId}`"
                  :style="{ '--accent': qualityAccent(row.score) }"
                >
                  <div class="ledger-head">
                    <div>
                      <strong>{{ row.name }}</strong>
                      <small>{{ row.datasetId }} · {{ row.auditSummary }}</small>
                    </div>
                    <span :class="qualityClass(row.reviewStatus)">{{ row.reviewStatus }}</span>
                  </div>
                  <button class="report-link" type="button" @click="openQualityReport(row.datasetId)">
                    查看质量报告
                  </button>
                  <div class="audit-item-grid">
                    <div
                      v-for="item in row.auditItems"
                      :key="`${row.datasetId}-${item.key}`"
                      :class="qualityClass(item.status)"
                    >
                      <span>{{ item.status }}</span>
                      <strong>{{ item.label }}</strong>
                      <small>{{ item.score }} 分 · {{ item.evidence }}</small>
                      <em>{{ item.action }}</em>
                    </div>
                  </div>
                  <p class="missing-fields">
                    <b>需补字段：</b>{{ row.missingFields.join('；') }}
                  </p>
                </article>
              </div>
            </section>

            <section class="issue-panel">
              <div class="section-head compact">
                <div>
                  <h2>待处理质量问题</h2>
                  <p>优先处理高风险项，再补齐可追溯字段和单位说明。</p>
                </div>
              </div>
              <div v-if="!qualityIssueRows.length" class="state small">当前未发现明显质量风险。</div>
              <article
                v-for="issue in qualityIssueRows"
                :key="`${issue.datasetId}-${issue.title}`"
                :class="qualityClass(issue.severity)"
              >
                <span>{{ issue.severity }}</span>
                <div>
                  <strong>{{ issue.title }}</strong>
                  <small>{{ issue.datasetName }}</small>
                  <p>{{ issue.detail }}</p>
                  <em>{{ issue.suggestion }}</em>
                </div>
              </article>
            </section>

            <aside class="calibration-panel">
              <span>Calibration checklist</span>
              <h3>建议纳入正式入库门控</h3>
              <ul>
                <li>原子数 = structure_json 中原子数量，composition 元素数可解释。</li>
                <li>能量、力、距离、带隙等字段必须有单位和理论层级。</li>
                <li>每条记录保留 source_record_id，可追溯到原始 HDF5/LMDB/CSV/extxyz。</li>
                <li>结构数据需检查 NaN、重复原子、异常键长、晶胞缺失和周期性标记。</li>
                <li>数据集级别必须提供论文链接、数据下载链接或来源说明。</li>
                <li>warning 应标准化为可筛选质量标签，而不是自由文本。</li>
              </ul>
            </aside>
          </div>
        </template>
      </section>

      <div v-if="qualityReport || qualityReportLoading || qualityReportError" class="report-modal">
        <div class="report-dialog">
          <div class="report-head">
            <div>
              <span>Dataset quality report</span>
              <h2>{{ qualityReport?.summary?.name || '质量报告' }}</h2>
            </div>
            <button type="button" @click="closeQualityReport">关闭</button>
          </div>
          <div v-if="qualityReportLoading" class="state small">正在生成质量报告...</div>
          <div v-else-if="qualityReportError" class="state error">{{ qualityReportError }}</div>
          <template v-else-if="qualityReport">
            <div class="report-score-grid">
              <article>
                <span>综合评分</span>
                <strong>{{ qualityReport.summary.score }}</strong>
                <small>{{ qualityReport.summary.level }}</small>
              </article>
              <article>
                <span>发布等级</span>
                <strong>{{ qualityReport.publication.grade }}</strong>
                <small>{{ qualityReport.publication.published ? '已发布' : '未公开' }}</small>
              </article>
              <article>
                <span>问题数</span>
                <strong>{{ qualityReport.issues.length }}</strong>
                <small>{{ qualityReport.summary.reviewStatus }}</small>
              </article>
              <article>
                <span>Run ID</span>
                <strong>{{ qualityReport.runId }}</strong>
                <small>{{ qualityReport.publication.updatedBy || 'system' }}</small>
              </article>
            </div>
            <div class="report-columns">
              <section>
                <h3>字段字典</h3>
                <div class="report-field-row head">
                  <span>字段</span>
                  <span>覆盖</span>
                  <span>单位</span>
                </div>
                <div v-for="field in qualityReport.fieldDictionary" :key="field.fieldKey" class="report-field-row">
                  <strong>{{ field.label }}</strong>
                  <span>{{ formatPercent(field.coverage) }}</span>
                  <small>{{ field.unit || '-' }}</small>
                </div>
              </section>
              <section>
                <h3>问题台账</h3>
                <article v-if="!qualityReport.issues.length" class="report-issue ok">当前未发现明显阻断项。</article>
                <article v-for="issue in qualityReport.issues" :key="`${issue.datasetId}-${issue.title}`" class="report-issue">
                  <span>{{ issue.severity }}</span>
                  <strong>{{ issue.title }}</strong>
                  <p>{{ issue.detail }}</p>
                </article>
              </section>
            </div>
            <pre class="report-text">{{ qualityReport.reportText }}</pre>
          </template>
        </div>
      </div>

      <section v-show="activeTab === 'model'" class="model-section">
        <div class="model-hero">
          <div>
            <p class="eyebrow">Model workbench</p>
            <h2>从 VASP 计算数据到可复现 AI 模型</h2>
            <p>
              模型页用于说明平台数据如何服务机器学习势函数、材料性质预测和主动学习闭环。当前版本先展示数据就绪度、训练流程和质量门控，后续可接入真实训练任务与模型版本库。
            </p>
            <div class="model-actions">
              <button type="button" @click="openExplore">选择训练数据</button>
              <button class="secondary" type="button" @click="openAssistant">询问智能助手</button>
            </div>
          </div>
          <div class="model-stats">
            <article v-for="stat in modelDashboardStats" :key="stat.label">
              <strong>{{ stat.value }}</strong>
              <span>{{ stat.label }}</span>
              <small>{{ stat.note }}</small>
            </article>
          </div>
        </div>

        <section class="model-builder">
          <div class="builder-panel">
            <div class="section-head compact">
              <div>
                <h2>模型任务配置</h2>
                <p>选择任务类型和目标标签，系统会按字段可用性、结构信息和数据规模推荐训练数据集。</p>
              </div>
              <button type="button" @click="resetModelPlanner">重置</button>
            </div>

            <div class="task-switcher">
              <button
                v-for="task in modelPlannerTasks"
                :key="task.id"
                type="button"
                :class="{ active: selectedModelTask === task.id }"
                @click="selectModelTask(task.id)"
              >
                <strong>{{ task.label }}</strong>
                <span>{{ task.description }}</span>
              </button>
            </div>

            <div class="model-form-grid">
              <label>
                <span>目标性质</span>
                <select v-model="targetProperty">
                  <option v-for="target in modelTargets" :key="target.id" :value="target.id">{{ target.label }}</option>
                </select>
              </label>
              <label>
                <span>限定元素</span>
                <select v-model="requiredElement">
                  <option value="">不限元素</option>
                  <option v-for="element in modelElementOptions" :key="element" :value="element">{{ element }}</option>
                </select>
              </label>
              <label>
                <span>原子数上限</span>
                <input v-model="modelAtomLimit" type="number" min="0" placeholder="不限">
              </label>
              <label>
                <span>随机种子</span>
                <input v-model="splitSeed" type="number" min="1">
              </label>
              <label class="check-line">
                <input v-model="requireStructure" type="checkbox">
                <span>需要三维结构</span>
              </label>
              <label class="check-line">
                <input v-model="requireForces" type="checkbox">
                <span>需要力标签</span>
              </label>
              <label>
                <span>验证集比例</span>
                <input v-model="validationRatio" type="number" min="0" max="40">
              </label>
              <label>
                <span>测试集比例</span>
                <input v-model="testRatio" type="number" min="0" max="40">
              </label>
            </div>
          </div>

          <aside class="plan-panel">
            <span>Training plan</span>
            <h3>{{ currentPlannerTask.label }}</h3>
            <p>{{ currentPlannerTask.description }}</p>
            <div class="split-bars">
              <div>
                <strong>{{ trainRatio }}%</strong>
                <span>训练</span>
              </div>
              <div>
                <strong>{{ validationRatio }}%</strong>
                <span>验证</span>
              </div>
              <div>
                <strong>{{ testRatio }}%</strong>
                <span>测试</span>
              </div>
            </div>
            <dl>
              <div>
                <dt>目标标签</dt>
                <dd>{{ currentTarget.label }}</dd>
              </div>
              <div>
                <dt>候选数据集</dt>
                <dd>{{ modelCandidateRows.length }} 个</dd>
              </div>
              <div>
                <dt>已选数据集</dt>
                <dd>{{ chosenModelDatasets.length }} 个</dd>
              </div>
            </dl>
            <button type="button" @click="exportModelPlan">导出训练计划 JSON</button>
            <button class="secondary" type="button" @click="openModelExplore">去数据发现继续筛选</button>
            <p v-if="modelPlanNotice" class="plan-notice">{{ modelPlanNotice }}</p>
          </aside>
        </section>

        <section class="candidate-panel">
          <div class="section-head compact">
            <div>
              <h2>候选数据集匹配</h2>
              <p>默认使用评分最高的前三个数据集；也可以手动勾选，导出的训练计划会采用手动选择。</p>
            </div>
          </div>
          <div v-if="!modelCandidateRows.length" class="state small">当前条件下没有匹配数据集，请放宽元素、原子数或标签要求。</div>
          <div v-else class="candidate-list">
            <article
              v-for="row in modelCandidateRows"
              :key="row.item.id"
              class="candidate-card"
              :class="{ selected: selectedModelDatasetIds.includes(row.item.id) }"
              :style="{ '--accent': metaFor(row.item.id).accent }"
            >
              <label class="candidate-check">
                <input
                  type="checkbox"
                  :checked="selectedModelDatasetIds.includes(row.item.id)"
                  @change="toggleModelDataset(row.item.id)"
                >
                <span>{{ row.scoreLabel }}</span>
              </label>
              <div>
                <h3>{{ row.item.name }}</h3>
                <p>{{ modelTaskFor(row.item) }} · {{ row.item.scale }}</p>
                <div class="candidate-tags">
                  <em v-for="reason in row.reasons" :key="reason">{{ reason }}</em>
                </div>
                <small>{{ row.risks.join('；') }}</small>
              </div>
              <button type="button" @click="openDataset(row.item)">查看数据</button>
            </article>
          </div>
        </section>

        <section class="training-panel">
          <div class="section-head compact">
            <div>
              <h2>快速基线评估</h2>
              <p>从当前选中的数据集中读取少量真实记录样本，在前端临时拟合一个轻量能量回归基线，用于快速检查字段、量纲和样本质量是否足以进入正式建模流程。</p>
            </div>
            <div class="training-actions">
              <label>
                <span>每个数据集采样</span>
                <input v-model="modelSampleLimit" type="number" min="12" max="80">
              </label>
              <button type="button" :disabled="trainingRunning || !chosenModelDatasets.length" @click="runQuickBaseline">
                {{ trainingRunning ? '评估中...' : '运行基线评估' }}
              </button>
              <button class="secondary" type="button" :disabled="!trainingResult" @click="exportTrainingResult">导出结果</button>
            </div>
          </div>

          <div v-if="trainingResult" class="training-results">
            <article>
              <span>样本</span>
              <strong>{{ trainingResult.sampleCount }}</strong>
              <small>{{ trainingResult.datasetCount }} 个数据集</small>
            </article>
            <article>
              <span>测试 MAE</span>
              <strong>{{ formatMetric(trainingResult.metrics.test.mae) }}</strong>
              <small>能量字段同单位前提下比较</small>
            </article>
            <article>
              <span>测试 RMSE</span>
              <strong>{{ formatMetric(trainingResult.metrics.test.rmse) }}</strong>
              <small>{{ trainingResult.type }}</small>
            </article>
            <article>
              <span>测试 R2</span>
              <strong>{{ formatMetric(trainingResult.metrics.test.r2) }}</strong>
              <small>{{ trainingResult.generatedAt }}</small>
            </article>
          </div>

          <div v-if="trainingResult" class="training-detail">
            <div>
              <h3>数据划分</h3>
              <p>训练 {{ trainingResult.split.train }} / 验证 {{ trainingResult.split.validation }} / 测试 {{ trainingResult.split.test }}</p>
              <p class="warning">{{ trainingResult.warning }}</p>
            </div>
            <div>
              <h3>主要特征权重</h3>
              <div class="feature-list">
                <span v-for="feature in trainingResult.topFeatures" :key="feature.name">
                  {{ feature.name }} <strong>{{ formatMetric(feature.weight) }}</strong>
                </span>
              </div>
            </div>
          </div>

          <div class="training-log">
            <span>运行日志</span>
            <p v-if="!trainingLogs.length">尚未运行基线评估。</p>
            <p v-for="line in trainingLogs" :key="line">{{ line }}</p>
          </div>
        </section>

        <div class="model-task-grid">
          <article v-for="task in modelTasks" :key="task.title" class="model-task-card">
            <span>{{ task.tag }}</span>
            <h3>{{ task.title }}</h3>
            <p>{{ task.text }}</p>
            <div>
              <em v-for="metric in task.metrics" :key="metric">{{ metric }}</em>
            </div>
          </article>
        </div>

        <div class="model-workspace">
          <section class="model-pipeline">
            <div class="section-head compact">
              <div>
                <h2>训练管线</h2>
                <p>让专家看到每个模型结果来自哪批数据、哪些标签和哪套计算设置。</p>
              </div>
            </div>
            <div class="model-stage-list">
              <article v-for="stage in modelStages" :key="stage.step">
                <i>{{ stage.step }}</i>
                <strong>{{ stage.title }}</strong>
                <p>{{ stage.text }}</p>
              </article>
            </div>
          </section>

          <aside class="quality-panel">
            <span>Quality gates</span>
            <h3>模型发布前检查</h3>
            <ul>
              <li v-for="item in qualityChecks" :key="item">{{ item }}</li>
            </ul>
          </aside>
        </div>

        <section class="model-readiness">
          <div class="section-head compact">
            <div>
              <h2>当前数据集建模就绪度</h2>
              <p>用于快速判断每个数据集更适合训练什么模型，以及还缺哪些质量信息。</p>
            </div>
          </div>
          <div class="readiness-table">
            <div class="readiness-row head">
              <span>数据集</span>
              <span>推荐模型任务</span>
              <span>主要方法</span>
              <span>规模</span>
              <span>状态</span>
            </div>
            <button
              v-for="row in modelDatasetRows"
              :key="row.id"
              class="readiness-row"
              type="button"
              @click="openDataset({ id: row.id, linkable: true })"
            >
              <strong>{{ row.name }}</strong>
              <span>{{ row.task }}</span>
              <span>{{ row.method }}</span>
              <span>{{ row.scale }}</span>
              <em>{{ row.readiness }}</em>
            </button>
          </div>
        </section>
      </section>
      <section v-show="activeTab === 'workflow'" class="workflow-section">
        <div class="workflow-hero">
          <div>
            <p class="eyebrow">VASP workflow</p>
            <h2>计算任务到标准数据的生产流水线</h2>
            <p>
              工作流页面用于展示 VASP 计算数据如何从结构输入、任务提交、结果解析、质量校验进入统一数据库，并继续服务数据发现、智能助手和模型训练。
            </p>
            <div class="workflow-actions">
              <button type="button" @click="openExplore">查看已入库数据</button>
              <button class="secondary" type="button" @click="openTab('model')">进入模型回流</button>
            </div>
          </div>
          <div class="workflow-metrics">
            <article v-for="metric in workflowMetrics" :key="metric.label">
              <strong>{{ typeof metric.value === 'number' ? formatCompact(metric.value) : metric.value }}</strong>
              <span>{{ metric.label }}</span>
              <small>{{ metric.unit }}</small>
            </article>
          </div>
        </div>

        <section class="workflow-flow">
          <article
            v-for="stage in workflowStages"
            :key="stage.step"
            class="workflow-stage"
            :class="stage.status"
          >
            <i>{{ stage.step }}</i>
            <span>{{ stage.status }}</span>
            <h3>{{ stage.title }}</h3>
            <p>{{ stage.text }}</p>
          </article>
        </section>

        <div class="workflow-grid">
          <section class="queue-panel">
            <div class="section-head compact">
              <div>
                <h2>任务队列概览</h2>
                <p>当前为面向专家汇报的前端状态面板，后续可对接真实调度器和服务器日志。</p>
              </div>
            </div>
            <div class="queue-list">
              <article v-for="queue in workflowQueues" :key="queue.name">
                <div>
                  <strong>{{ queue.name }}</strong>
                  <span>{{ queue.note }}</span>
                </div>
                <em :class="queue.state">{{ queue.state }}</em>
                <b>{{ queue.count.toLocaleString('zh-CN') }}</b>
              </article>
            </div>
          </section>

          <aside class="artifact-panel">
            <span>Artifacts</span>
            <h3>每个任务应沉淀的产物</h3>
            <ul>
              <li v-for="item in workflowArtifacts" :key="item">{{ item }}</li>
            </ul>
          </aside>
        </div>

        <section class="workflow-rules">
          <div class="section-head compact">
            <div>
              <h2>质量门控</h2>
              <p>专家更关心数据能否复现、能否比较、能否训练模型，这些规则应在入库前自动检查。</p>
            </div>
          </div>
          <div class="rule-grid">
            <article v-for="rule in workflowRules" :key="rule.title">
              <strong>{{ rule.title }}</strong>
              <p>{{ rule.text }}</p>
            </article>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.shell {
  width: min(1840px, calc(100% - 64px));
  margin: 0 auto;
  padding: 34px 0 60px;
}

.dashboard-hero {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 22px;
  min-height: 320px;
  padding: 34px 28px 30px;
  border: 1px solid color-mix(in srgb, var(--vs-blue-300) 34%, var(--vs-border));
  border-radius: var(--vs-radius-lg);
  background: var(--vs-hero-grad);
  box-shadow: var(--vs-shadow-lg);
  overflow: hidden;
}

.dashboard-copy {
  display: flex;
  max-width: 860px;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 6px 0 0;
}

.dashboard-copy .eyebrow {
  margin-bottom: 12px;
  font-size: 11px;
  letter-spacing: var(--vs-tracking-eyebrow);
}

.dashboard-copy h1 {
  max-width: 760px;
  margin: 0;
  color: var(--vs-blue-950);
  font-size: var(--vs-type-display);
  line-height: 1.08;
  letter-spacing: var(--vs-tracking-tight);
}

.dashboard-copy p:not(.eyebrow) {
  max-width: 680px;
  margin: 14px 0 0;
  color: var(--vs-text-secondary);
  font-size: 15px;
  line-height: 1.7;
}

.dashboard-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 7px;
  margin-top: 18px;
}

.dashboard-tags span {
  border: 1px solid color-mix(in srgb, var(--vs-blue-300) 52%, var(--vs-border));
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-blue-100) 58%, var(--vs-card));
  color: var(--vs-blue-700);
  padding: 5px 9px;
  font-size: 11px;
  font-weight: 800;
}

.dashboard-scene-panel {
  position: relative;
  width: 100%;
  min-height: 320px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--vs-blue-300) 38%, var(--vs-border));
  border-radius: var(--vs-radius-lg);
  background:
    radial-gradient(circle at 50% 46%, rgba(47, 111, 237, 0.13), transparent 42%),
    radial-gradient(circle at 80% 22%, rgba(22, 181, 200, 0.1), transparent 26%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(232, 241, 255, 0.5));
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.72), var(--vs-shadow-md);
}

.dashboard-scene-panel::before {
  content: "";
  position: absolute;
  inset: 14px;
  border: 1px solid rgba(47, 111, 237, 0.1);
  border-radius: var(--vs-radius-md);
  pointer-events: none;
}

.dashboard-scene-panel::after {
  content: "";
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(47, 111, 237, 0.08) 1px, transparent 1px),
    linear-gradient(0deg, rgba(8, 146, 165, 0.06) 1px, transparent 1px),
    linear-gradient(115deg, transparent 0 44%, rgba(22, 181, 200, 0.12) 49%, transparent 54% 100%);
  background-size: 42px 42px, 42px 42px, 260px 100%;
  mask-image: radial-gradient(circle at 50% 50%, #000 0, transparent 72%);
  opacity: 0.58;
  pointer-events: none;
}

.scene-overlay {
  position: absolute;
  right: 14px;
  bottom: 12px;
  display: flex;
  gap: 6px;
  pointer-events: none;
}

.scene-overlay span {
  border: 1px solid color-mix(in srgb, var(--vs-cyan-500) 32%, var(--vs-border));
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-card) 78%, transparent);
  color: var(--vs-text-secondary);
  padding: 4px 7px;
  font-size: 10px;
  font-weight: 800;
  backdrop-filter: blur(10px);
}

.dashboard-kpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
  max-width: 920px;
}

.dashboard-overview {
  display: grid;
  grid-template-columns: minmax(220px, 0.38fr) minmax(720px, 1.85fr);
  gap: 12px;
  margin-top: 12px;
}

.dashboard-overview .explorer-card {
  min-height: 100%;
  padding: 18px;
  gap: 14px;
}

.dashboard-overview .explorer-card strong {
  font-size: 14px;
}

.dashboard-overview .explorer-card span {
  font-size: 11px;
}

.dashboard-overview .search-symbol {
  width: 38px;
  height: 38px;
  border-width: 2px;
}

.dashboard-overview :deep(.element-spectrum) {
  grid-column: 1 / -1;
}

.hero {
  min-height: 300px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--vs-card) 95%, var(--vs-primary)) 0, var(--vs-card) 58%, color-mix(in srgb, var(--vs-card) 86%, #75d3c6) 100%);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-lg);
  padding: 34px;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.8fr) 230px;
  gap: 26px;
  align-items: stretch;
  box-shadow: 0 18px 50px rgba(22, 49, 102, 0.07);
}

.hero-copy-block {
  display: flex;
  max-width: 920px;
  flex-direction: column;
  justify-content: center;
}

.eyebrow {
  margin: 0 0 12px;
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  max-width: 920px;
  font-size: clamp(34px, 4.2vw, 52px);
  line-height: 1.12;
  font-weight: 850;
  letter-spacing: 0;
}

.hero-copy {
  max-width: 820px;
  margin: 16px 0 0;
  color: var(--vs-text-secondary);
  font-size: 15px;
  line-height: 1.72;
}

.hero-tags,
.element-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-tags {
  margin-top: 22px;
}

.hero-tags span,
.element-chips span {
  border: 1px solid color-mix(in srgb, var(--vs-primary) 26%, var(--vs-border));
  background: color-mix(in srgb, var(--vs-primary) 9%, var(--vs-card));
  color: var(--vs-primary);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 800;
}

.hero-visual {
  position: relative;
  min-height: 232px;
  overflow: hidden;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background:
    radial-gradient(circle at 52% 42%, color-mix(in srgb, var(--vs-primary) 13%, transparent), transparent 34%),
    linear-gradient(145deg, color-mix(in srgb, var(--vs-card-soft) 92%, var(--vs-primary)), var(--vs-card));
  perspective: 760px;
  transform-style: preserve-3d;
}

.hero-visual::before,
.hero-visual::after {
  content: "";
  position: absolute;
  pointer-events: none;
}

.hero-visual::before {
  inset: auto 42px 20px 120px;
  height: 56px;
  border-radius: 50%;
  background: radial-gradient(ellipse at center, rgba(47, 111, 237, 0.16), transparent 68%);
  filter: blur(2px);
  transform: rotateX(68deg) translateZ(-90px);
}

.hero-visual::after {
  right: 78px;
  top: 42px;
  width: 118px;
  height: 118px;
  border: 1px solid color-mix(in srgb, var(--vs-primary) 18%, transparent);
  border-radius: 28px;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--vs-primary) 14%, transparent) 1px, transparent 1px),
    linear-gradient(0deg, color-mix(in srgb, var(--vs-primary) 14%, transparent) 1px, transparent 1px);
  background-size: 24px 24px;
  opacity: 0.7;
  transform: rotateX(58deg) rotateZ(42deg) translateZ(-34px);
}

.compute-chip {
  position: absolute;
  left: 18px;
  top: 18px;
  z-index: 2;
  display: grid;
  gap: 5px;
  padding: 12px 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-card) 92%, transparent);
  box-shadow: 0 10px 24px rgba(29, 33, 41, 0.06);
  transform: translateZ(42px);
}

.compute-chip span {
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
}

.compute-chip strong {
  color: var(--vs-text);
  font-size: 14px;
}

.orbital {
  position: absolute;
  inset: 32px 44px 42px;
  display: grid;
  place-items: center;
  transform: rotateX(58deg) rotateZ(-12deg) translateZ(18px);
  transform-style: preserve-3d;
}

.orbital i {
  position: absolute;
  width: 236px;
  height: 98px;
  border: 1.5px solid color-mix(in srgb, var(--vs-primary) 46%, transparent);
  border-radius: 50%;
  box-shadow: 0 0 18px color-mix(in srgb, var(--vs-primary) 12%, transparent);
  animation: orbital-drift 12s linear infinite;
}

.orbital i:nth-child(1) {
  transform: rotateZ(22deg) rotateY(18deg);
}

.orbital i:nth-child(2) {
  transform: rotateZ(83deg) rotateY(-24deg);
  animation-duration: 15s;
}

.orbital i:nth-child(3) {
  transform: rotateZ(142deg) rotateY(34deg);
  animation-duration: 18s;
}

@keyframes orbital-drift {
  from {
    filter: hue-rotate(0deg);
  }

  to {
    filter: hue-rotate(14deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .orbital i,
  .dataset-card,
  .element-bar {
    animation: none;
    transition: none;
  }

  .dataset-card:hover,
  .element-bar:hover {
    transform: none;
  }
}

.atom {
  position: absolute;
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 50%;
  background: var(--vs-card);
  border: 1px solid var(--vs-border);
  color: var(--vs-text);
  font-size: 13px;
  font-weight: 900;
  box-shadow:
    inset -8px -10px 16px rgba(22, 32, 52, 0.16),
    inset 7px 8px 14px rgba(255, 255, 255, 0.35),
    0 16px 30px rgba(29, 33, 41, 0.16);
  transform-style: preserve-3d;
}

.atom::after {
  content: "";
  position: absolute;
  inset: 6px auto auto 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.72);
}

.atom-a {
  left: 48%;
  top: 45%;
  background: #43a867;
  color: #fff;
  transform: translateZ(64px) scale(1.12);
}

.atom-b {
  right: 18%;
  top: 22%;
  background: #df6a62;
  color: #fff;
  transform: translateZ(38px);
}

.atom-c {
  left: 18%;
  bottom: 18%;
  background: #3f7edb;
  color: #fff;
  transform: translateZ(26px) scale(0.96);
}

.atom-d {
  right: 24%;
  bottom: 16%;
  transform: translateZ(54px);
}

.lattice {
  position: absolute;
  right: 20px;
  bottom: 18px;
  display: grid;
  grid-template-columns: repeat(4, 18px);
  gap: 8px;
  transform: rotateX(62deg) rotateZ(0deg) translateZ(14px);
  transform-style: preserve-3d;
}

.lattice span {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: color-mix(in srgb, var(--vs-primary) 18%, var(--vs-card));
  border: 1px solid color-mix(in srgb, var(--vs-primary) 32%, var(--vs-border));
  box-shadow:
    0 10px 16px rgba(47, 111, 237, 0.11),
    inset -4px -5px 9px rgba(47, 111, 237, 0.16),
    inset 4px 4px 8px rgba(255, 255, 255, 0.62);
  transform: translateZ(calc((var(--i, 1)) * 1px));
}

.lattice span:nth-child(4n + 1) {
  --i: 6;
}

.lattice span:nth-child(4n + 2) {
  --i: 12;
}

.lattice span:nth-child(4n + 3) {
  --i: 8;
}

.lattice span:nth-child(4n) {
  --i: 16;
}

.hero-metrics {
  display: grid;
  align-content: center;
  gap: 14px;
}

.hero-metrics div,
.state,
.dataset-card {
  background: var(--vs-card);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
}

.hero-metrics div {
  padding: 22px 20px;
  box-shadow: 0 10px 28px rgba(29, 33, 41, 0.05);
}

.hero-metrics strong {
  display: block;
  color: var(--vs-primary);
  font-size: 32px;
  line-height: 1;
}

.hero-metrics span {
  display: block;
  margin-top: 10px;
  color: var(--vs-text-secondary);
  font-size: 14px;
}

.insight-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.72fr) minmax(330px, 1fr) minmax(360px, 1.2fr);
  gap: 16px;
  margin-top: 16px;
}

.explorer-card,
.domain-card,
.element-board {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-card) 95%, transparent);
  box-shadow: 0 8px 22px rgba(29, 33, 41, 0.04);
}

.explorer-card {
  min-height: 154px;
  padding: 22px;
  display: flex;
  align-items: center;
  gap: 18px;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.explorer-card:hover {
  border-color: var(--vs-border-strong);
  background: color-mix(in srgb, var(--vs-primary) 5%, var(--vs-card));
}

.search-symbol {
  position: relative;
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border: 3px solid var(--vs-primary);
  border-radius: 50%;
}

.search-symbol::after {
  content: "";
  position: absolute;
  right: -10px;
  bottom: 2px;
  width: 17px;
  height: 3px;
  border-radius: 999px;
  background: var(--vs-primary);
  transform: rotate(45deg);
}

.explorer-card span,
.domain-head span,
.home-capabilities span,
.dataset-card-action span {
  display: block;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  line-height: 1.4;
}

.explorer-card strong,
.domain-head strong {
  display: block;
  margin-top: 6px;
  color: var(--vs-text);
  font-size: 15px;
  line-height: 1.45;
}

.domain-card,
.element-board {
  padding: 18px;
  overflow: hidden;
  perspective: 900px;
}

.domain-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.domain-head strong {
  margin: 0;
}

.domain-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.domain-item {
  min-height: 86px;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--accent) 28%, var(--vs-border));
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--accent) 8%, var(--vs-card));
}

.domain-item > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 26px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 11px;
  font-weight: 900;
}

.domain-item strong,
.domain-item small {
  display: block;
}

.domain-item strong {
  margin-top: 10px;
  color: var(--vs-text);
  font-size: 13px;
}

.domain-item small {
  margin-top: 5px;
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: 1.35;
}

.element-bars {
  height: 126px;
  display: grid;
  grid-template-columns: repeat(18, minmax(0, 1fr));
  gap: 8px;
  align-items: end;
  margin-top: 18px;
  padding: 0 4px 8px;
  border-radius: var(--vs-radius-md);
  background:
    linear-gradient(180deg, transparent 0, transparent 74%, color-mix(in srgb, var(--vs-primary) 6%, transparent) 100%);
  transform: rotateX(8deg);
  transform-style: preserve-3d;
}

.element-bar {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 7px;
  transform-style: preserve-3d;
  transition: transform 0.18s ease;
}

.element-bar:hover {
  transform: translateY(-4px) translateZ(12px);
}

.element-bar span {
  position: relative;
  width: 100%;
  max-width: 22px;
  min-height: 24px;
  border-radius: 10px 10px 4px 4px;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--vs-primary) 78%, #ffffff) 0, var(--vs-primary) 46%, color-mix(in srgb, var(--vs-primary) 62%, #102650) 100%);
  box-shadow:
    inset 6px 0 9px rgba(255, 255, 255, 0.28),
    inset -6px 0 10px rgba(18, 37, 72, 0.2),
    0 14px 18px rgba(47, 111, 237, 0.14);
  transform: rotateX(-8deg) translateZ(10px);
  transform-style: preserve-3d;
}

.element-bar span::before,
.element-bar span::after {
  content: "";
  position: absolute;
  pointer-events: none;
}

.element-bar span::before {
  inset: -7px 2px auto;
  height: 12px;
  border-radius: 50%;
  background: radial-gradient(ellipse at 35% 35%, rgba(255, 255, 255, 0.82), color-mix(in srgb, var(--vs-primary) 76%, #ffffff) 44%, var(--vs-primary) 100%);
  box-shadow: 0 2px 7px rgba(47, 111, 237, 0.16);
}

.element-bar span::after {
  left: 4px;
  right: 4px;
  bottom: -10px;
  height: 12px;
  border-radius: 50%;
  background: rgba(47, 111, 237, 0.13);
  filter: blur(3px);
  transform: translateZ(-14px);
}

.element-bar strong {
  color: var(--vs-text-secondary);
  font-size: 11px;
  transform: translateZ(18px);
}

.section {
  margin-top: 28px;
}

.review-section {
  margin-top: 28px;
  display: grid;
  gap: 18px;
}

.review-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: end;
  padding: 26px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-lg);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--vs-card) 92%, #43a867), var(--vs-card));
}

.review-header h2 {
  margin: 0;
  font-size: 28px;
}

.review-header p {
  max-width: 880px;
  margin: 10px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.65;
}

.review-header button {
  flex: 0 0 auto;
  height: 42px;
  padding: 0 16px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.concern-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.concern-card,
.evidence-panel,
.demo-card {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  box-shadow: 0 8px 22px rgba(29, 33, 41, 0.04);
}

.concern-card {
  padding: 18px;
}

.concern-card span,
.panel-title span,
.demo-card span {
  display: block;
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
}

.concern-card p {
  margin: 9px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.65;
}

.evidence-panel {
  padding: 22px;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
}

.panel-title strong {
  color: var(--vs-text);
  font-size: 18px;
}

.evidence-flow {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.evidence-step {
  min-height: 170px;
  padding: 16px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.evidence-step i {
  display: inline-grid;
  width: 34px;
  height: 28px;
  place-items: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-primary) 13%, var(--vs-card));
  color: var(--vs-primary);
  font-style: normal;
  font-size: 12px;
  font-weight: 900;
}

.evidence-step strong {
  display: block;
  margin-top: 12px;
  color: var(--vs-text);
  font-size: 15px;
}

.evidence-step p,
.demo-card p {
  margin: 9px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.65;
}

.demo-grid {
  display: grid;
  grid-template-columns: minmax(340px, 1.3fr) repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.demo-card {
  padding: 20px;
}

.demo-card h3 {
  margin: 10px 0 0;
  font-size: 22px;
}

.demo-card.compact {
  background: color-mix(in srgb, var(--vs-primary) 5%, var(--vs-card));
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: end;
  margin-bottom: 18px;
}

.section-head h2 {
  margin: 0;
  color: var(--vs-text);
  font-size: 20px;
  font-weight: 850;
}

.section-head p {
  margin: 7px 0 0;
  color: var(--vs-text-tertiary);
  font-size: 14px;
}

.dataset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.dataset-card {
  position: relative;
  display: flex;
  min-height: 350px;
  flex-direction: column;
  padding: 22px;
  cursor: pointer;
  overflow: hidden;
  transform-style: preserve-3d;
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease, background 0.16s ease;
}

.dataset-card::before {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 12%, transparent), transparent 38%),
    radial-gradient(circle at 18% 12%, color-mix(in srgb, var(--accent) 14%, transparent), transparent 28%);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.16s ease;
}

.dataset-card:hover {
  transform: translateY(-4px) rotateX(1.4deg) rotateY(-1.2deg);
  border-color: color-mix(in srgb, var(--accent) 42%, var(--vs-border-strong));
  background: color-mix(in srgb, var(--vs-card) 95%, var(--accent));
  box-shadow:
    0 24px 44px color-mix(in srgb, var(--accent) 16%, transparent),
    0 2px 0 color-mix(in srgb, var(--accent) 28%, transparent);
}

.dataset-card:hover::before {
  opacity: 1;
}

.card-top {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: flex-start;
  gap: 14px;
}

.dataset-mark {
  position: relative;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 92%, #fff), var(--accent));
  box-shadow: 0 12px 24px color-mix(in srgb, var(--accent) 26%, transparent);
  color: #fff;
  font-size: 13px;
  font-weight: 900;
  transform: translateZ(18px);
}

.dataset-mark::after {
  content: "";
  position: absolute;
  inset: 8px auto auto 10px;
  width: 10px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.42);
}

.card-title h3 {
  margin: 0;
  color: var(--vs-text);
  font-size: 17px;
  font-weight: 800;
  line-height: 1.35;
}

.card-title p,
.intro {
  color: var(--vs-text-secondary);
}

.card-title p {
  margin: 8px 0 0;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.pill {
  height: fit-content;
  max-width: 160px;
  padding: 6px 9px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 11%, var(--vs-card));
  color: var(--accent);
  border: 1px solid color-mix(in srgb, var(--accent) 24%, var(--vs-border));
  font-size: 12px;
  font-weight: 800;
  line-height: 1.25;
}

.intro {
  min-height: 78px;
  margin: 14px 0 14px;
  line-height: 1.68;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.element-chips {
  margin-bottom: 14px;
}

.element-chips span {
  border-color: color-mix(in srgb, var(--accent) 22%, var(--vs-border));
  background: color-mix(in srgb, var(--accent) 7%, var(--vs-card));
  color: var(--vs-text-secondary);
  padding: 5px 9px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: auto;
  gap: 10px;
}

.stats div {
  min-height: 78px;
  background: var(--vs-card-soft);
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  padding: 12px;
}

.stats span {
  display: block;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  margin-bottom: 7px;
}

.stats strong {
  color: var(--vs-text);
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.dataset-card-action {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--accent) 32%, var(--vs-border-strong));
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--accent) 10%, var(--vs-card));
  color: var(--accent);
  transform: translateZ(10px);
}

.dataset-card-action strong {
  color: var(--accent);
  font-size: 13px;
  line-height: 1.2;
}

.state {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--vs-text-secondary);
}

.state.error {
  color: #cc5656;
}

.quality-section {
  display: grid;
  gap: 18px;
  margin-top: 28px;
}

.quality-hero,
.quality-table-panel,
.issue-panel,
.calibration-panel,
.audit-flow-panel,
.audit-rule-panel,
.audit-ledger-panel,
.preflight-panel,
.publication-panel,
.quality-locked-panel {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-card) 94%, transparent);
  box-shadow: 0 10px 26px rgba(29, 33, 41, 0.04);
}

.quality-locked-panel {
  padding: 20px;
}

.quality-locked-panel strong {
  display: block;
  color: var(--vs-text);
  font-size: 17px;
}

.quality-locked-panel p {
  max-width: 900px;
  margin: 8px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.7;
}

.quality-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: end;
  padding: 28px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--vs-card) 92%, #43a867), var(--vs-card) 62%, color-mix(in srgb, var(--vs-card) 90%, #3f7edb));
}

.quality-hero h2 {
  margin: 0;
  font-size: clamp(28px, 3vw, 42px);
  line-height: 1.16;
}

.quality-hero p {
  max-width: 920px;
  margin: 14px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.72;
}

.quality-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  flex: 0 0 auto;
}

.quality-actions button {
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 850;
}

.quality-actions button.secondary {
  background: color-mix(in srgb, var(--vs-primary) 8%, var(--vs-card));
  color: var(--vs-primary);
}

.quality-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.quality-summary article {
  min-height: 118px;
  padding: 18px;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--vs-border));
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--accent) 7%, var(--vs-card));
}

.quality-summary span,
.quality-summary strong,
.quality-summary small,
.quality-scope strong,
.quality-scope span,
.quality-scope em {
  display: block;
}

.quality-summary span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 850;
}

.quality-summary strong {
  margin-top: 8px;
  color: var(--accent);
  font-size: 32px;
  line-height: 1;
}

.quality-summary small {
  margin-top: 9px;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.quality-scope {
  display: grid;
  gap: 6px;
  padding: 16px 18px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
}

.quality-scope strong {
  color: var(--vs-text);
  font-size: 14px;
}

.quality-scope span,
.quality-scope em {
  color: var(--vs-text-secondary);
  font-size: 13px;
  font-style: normal;
  line-height: 1.55;
}

.quality-gates {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.quality-gates article {
  min-height: 180px;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--accent) 24%, var(--vs-border));
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--accent) 7%, var(--vs-card));
}

.gate-ring {
  width: 66px;
  height: 66px;
  display: grid;
  place-items: center;
  align-content: center;
  border-radius: 50%;
  border: 6px solid color-mix(in srgb, var(--accent) 34%, var(--vs-border));
  background: var(--vs-card);
}

.gate-ring strong {
  color: var(--accent);
  font-size: 20px;
  line-height: 1;
}

.gate-ring span {
  margin-top: 3px;
  color: var(--vs-text-tertiary);
  font-size: 10px;
  font-weight: 850;
}

.quality-gates h3 {
  margin: 14px 0 0;
  color: var(--vs-text);
  font-size: 16px;
}

.quality-gates p {
  margin: 8px 0 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.quality-table-panel,
.issue-panel,
.calibration-panel,
.audit-flow-panel,
.audit-rule-panel,
.audit-ledger-panel,
.preflight-panel,
.publication-panel {
  padding: 22px;
}

.feedback {
  margin: 0;
  padding: 12px 14px;
  border-radius: var(--vs-radius-sm);
  font-size: 13px;
  font-weight: 800;
}

.feedback.error {
  border: 1px solid rgba(207, 95, 95, 0.28);
  background: rgba(207, 95, 95, 0.08);
  color: #bd4f4f;
}

.feedback.success {
  border: 1px solid rgba(47, 143, 107, 0.24);
  background: rgba(47, 143, 107, 0.08);
  color: #2f8f6b;
}

.preflight-panel {
  display: grid;
  gap: 16px;
}

.preflight-panel .section-head button,
.link-checker button,
.publication-list button,
.preflight-login button {
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 900;
}

.preflight-panel button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.preflight-login {
  display: grid;
  gap: 8px;
  padding: 18px;
  border: 1px dashed var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
}

.preflight-login strong {
  color: var(--vs-text);
  font-size: 15px;
}

.preflight-login span {
  color: var(--vs-text-secondary);
  font-size: 13px;
}

.preflight-login button {
  width: fit-content;
  margin-top: 4px;
}

.preflight-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
  align-items: start;
}

.link-checker,
.file-previewer {
  display: grid;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
}

.link-checker h3,
.file-previewer h3 {
  margin: 0;
  color: var(--vs-text);
  font-size: 17px;
}

.link-checker label,
.publication-note {
  display: grid;
  gap: 6px;
}

.link-checker label span,
.publication-note span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 850;
}

.link-checker input,
.publication-note input {
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text);
  font: inherit;
}

.link-result {
  display: grid;
  gap: 8px;
}

.link-result > strong {
  width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(47, 143, 107, 0.1);
  color: #2f8f6b;
  font-size: 13px;
}

.link-result article {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
}

.link-result article > span {
  display: inline-grid;
  height: 26px;
  place-items: center;
  border-radius: 999px;
  background: rgba(207, 95, 95, 0.1);
  color: #bd4f4f;
  font-size: 11px;
  font-weight: 900;
}

.link-result article.ok > span {
  background: rgba(47, 143, 107, 0.12);
  color: #2f8f6b;
}

.link-result b,
.link-result small,
.link-result em {
  display: block;
}

.link-result b {
  color: var(--vs-text);
  font-size: 13px;
}

.link-result small,
.link-result em {
  margin-top: 3px;
  color: var(--vs-text-secondary);
  font-size: 11px;
  font-style: normal;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.file-drop {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 132px;
  padding: 20px;
  border: 1px dashed var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  cursor: pointer;
  text-align: center;
}

.file-drop input {
  display: none;
}

.file-drop strong {
  color: var(--vs-primary);
  font-size: 16px;
}

.file-drop span {
  max-width: 520px;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.file-preview-result {
  display: grid;
  gap: 12px;
}

.preview-score {
  display: grid;
  gap: 5px;
  padding: 14px;
  border: 1px solid rgba(47, 143, 107, 0.22);
  border-radius: var(--vs-radius-sm);
  background: rgba(47, 143, 107, 0.07);
}

.preview-score strong {
  color: #2f8f6b;
  font-size: 34px;
  line-height: 1;
}

.preview-score span,
.preview-score small {
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.missing-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  align-items: center;
}

.missing-list b {
  color: var(--vs-text);
  font-size: 12px;
}

.missing-list span {
  padding: 5px 8px;
  border-radius: 999px;
  background: rgba(177, 132, 63, 0.12);
  color: #a06f2c;
  font-size: 11px;
  font-weight: 850;
}

.preflight-recommendations {
  display: grid;
  gap: 7px;
  padding: 12px;
  border: 1px solid rgba(47, 111, 237, 0.18);
  border-radius: var(--vs-radius-sm);
  background: rgba(47, 111, 237, 0.055);
}

.preflight-recommendations b {
  color: var(--vs-text);
  font-size: 12px;
}

.preflight-recommendations p {
  margin: 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.field-table {
  display: grid;
  gap: 6px;
  overflow-x: auto;
}

.field-row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) 90px 110px minmax(180px, 1.2fr);
  gap: 10px;
  align-items: center;
  min-width: 620px;
  padding: 9px 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.field-row.head {
  background: transparent;
  color: var(--vs-text-tertiary);
  font-weight: 900;
}

.field-row strong {
  color: var(--vs-text);
  overflow-wrap: anywhere;
}

.field-row small {
  color: var(--vs-text-secondary);
  overflow-wrap: anywhere;
}

.publication-panel {
  display: grid;
  gap: 14px;
}

.publication-stat {
  min-width: 150px;
  padding: 12px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.publication-stat strong,
.publication-stat span,
.publication-stat small {
  display: block;
}

.publication-stat strong {
  color: var(--vs-primary);
  font-size: 28px;
  line-height: 1;
}

.publication-stat span,
.publication-stat small {
  margin-top: 5px;
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.publication-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  max-height: 420px;
  overflow: auto;
}

.publication-list article {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.publication-list article.hidden {
  border-color: rgba(207, 95, 95, 0.22);
  background: rgba(207, 95, 95, 0.06);
}

.publication-list strong,
.publication-list small {
  display: block;
}

.publication-list strong {
  color: var(--vs-text);
  font-size: 13px;
}

.publication-list small {
  margin-top: 4px;
  color: var(--vs-text-tertiary);
  font-size: 11px;
  line-height: 1.4;
}

.publication-list button {
  flex: 0 0 auto;
  background: color-mix(in srgb, var(--vs-primary) 9%, var(--vs-card));
  color: var(--vs-primary);
}

.report-link {
  width: fit-content;
  margin: 10px 0 12px;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--vs-primary) 22%, var(--vs-border));
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-primary) 8%, var(--vs-card));
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
}

.report-modal {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.34);
  backdrop-filter: blur(8px);
}

.report-dialog {
  width: min(1080px, 100%);
  max-height: min(780px, calc(100vh - 48px));
  overflow: auto;
  padding: 18px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  box-shadow: var(--vs-shadow-lg);
}

.report-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.report-head span,
.report-score-grid span {
  color: var(--vs-text-tertiary);
  font-size: 11px;
  font-weight: 900;
  text-transform: uppercase;
}

.report-head h2 {
  margin: 4px 0 0;
  color: var(--vs-text);
  font-size: 24px;
}

.report-head button {
  background: color-mix(in srgb, var(--vs-primary) 9%, var(--vs-card));
  color: var(--vs-primary);
}

.report-score-grid,
.report-columns {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.report-score-grid article,
.report-columns section,
.report-text {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.report-score-grid article {
  padding: 14px;
}

.report-score-grid strong,
.report-score-grid small {
  display: block;
}

.report-score-grid strong {
  margin-top: 7px;
  color: var(--vs-primary);
  font-size: 24px;
  line-height: 1.1;
  overflow-wrap: anywhere;
}

.report-score-grid small {
  margin-top: 5px;
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.report-columns {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  margin-top: 10px;
}

.report-columns section {
  padding: 14px;
}

.report-columns h3 {
  margin: 0 0 10px;
  color: var(--vs-text);
  font-size: 15px;
}

.report-field-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 70px minmax(120px, 1fr);
  gap: 10px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--vs-border);
  font-size: 12px;
}

.report-field-row.head {
  color: var(--vs-text-tertiary);
  font-weight: 900;
}

.report-field-row strong {
  color: var(--vs-text);
}

.report-field-row small {
  color: var(--vs-text-secondary);
  overflow-wrap: anywhere;
}

.report-issue {
  padding: 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.report-issue + .report-issue {
  margin-top: 8px;
}

.report-issue span {
  color: #cf5f5f;
  font-weight: 900;
}

.report-issue strong {
  display: block;
  margin-top: 4px;
  color: var(--vs-text);
}

.report-issue p {
  margin: 5px 0 0;
  line-height: 1.5;
}

.report-issue.ok {
  color: #178f55;
}

.report-text {
  margin: 10px 0 0;
  padding: 14px;
  max-height: 260px;
  overflow: auto;
  color: var(--vs-text-secondary);
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.audit-flow {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.audit-flow article,
.audit-rule-grid article,
.audit-ledger-list article {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.audit-flow article {
  min-height: 190px;
  padding: 14px;
}

.audit-flow span,
.audit-rule-grid span {
  display: inline-flex;
  width: fit-content;
  padding: 5px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
  color: var(--vs-primary);
  font-size: 11px;
  font-weight: 900;
}

.audit-flow strong,
.audit-flow small,
.audit-flow p,
.audit-flow em,
.audit-rule-grid strong,
.audit-rule-grid p,
.audit-rule-grid em {
  display: block;
}

.audit-flow strong,
.audit-rule-grid strong {
  margin-top: 12px;
  color: var(--vs-text);
  font-size: 15px;
}

.audit-flow small {
  margin-top: 5px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.audit-flow p,
.audit-flow em,
.audit-rule-grid p,
.audit-rule-grid em {
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.audit-flow p,
.audit-rule-grid p {
  margin: 10px 0 0;
}

.audit-flow em,
.audit-rule-grid em {
  margin-top: 8px;
  font-style: normal;
}

.audit-status-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.audit-status-panel article {
  min-height: 96px;
  padding: 16px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
}

.audit-status-panel span,
.audit-status-panel strong,
.audit-status-panel small {
  display: block;
}

.audit-status-panel span {
  color: var(--vs-text-secondary);
  font-size: 13px;
  font-weight: 850;
}

.audit-status-panel strong {
  margin-top: 8px;
  color: var(--vs-text);
  font-size: 28px;
  line-height: 1;
}

.audit-status-panel small {
  margin-top: 7px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.audit-rule-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.audit-rule-grid article {
  min-height: 170px;
  padding: 16px;
}

.quality-table-panel {
  overflow-x: auto;
}

.quality-table {
  display: grid;
  gap: 8px;
  min-width: 1080px;
}

.quality-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.35fr) 112px repeat(4, 74px) minmax(132px, 0.8fr) minmax(250px, 1.2fr);
  gap: 12px;
  align-items: center;
  min-height: 68px;
  padding: 12px 14px;
  border: 1px solid var(--vs-border);
  border-left: 4px solid var(--accent, var(--vs-border));
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text);
  font: inherit;
  text-align: left;
}

.quality-row:not(.head) {
  cursor: pointer;
}

.quality-row:not(.head):hover {
  border-color: color-mix(in srgb, var(--accent) 52%, var(--vs-border));
  background: color-mix(in srgb, var(--accent) 6%, var(--vs-card));
}

.quality-row.head {
  min-height: 42px;
  border-left-color: transparent;
  background: transparent;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 900;
  cursor: default;
}

.quality-row strong {
  display: grid;
  gap: 5px;
  color: var(--vs-text);
  font-size: 14px;
}

.quality-row small {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 500;
}

.score-pill {
  display: inline-flex;
  width: fit-content;
  min-width: 88px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 12%, var(--vs-card));
  color: var(--accent);
  font-size: 12px;
  font-weight: 900;
}

.review-pill {
  display: grid;
  gap: 3px;
  width: fit-content;
  min-width: 118px;
  padding: 7px 10px;
  border-radius: 10px;
  background: var(--vs-card);
  color: var(--vs-text);
  font-size: 12px;
  font-weight: 900;
}

.review-pill small {
  max-width: 132px;
  overflow: hidden;
  color: var(--vs-text-tertiary);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-strip {
  position: relative;
  min-height: 42px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: end;
  padding-top: 14px;
}

.metric-strip i {
  position: absolute;
  left: 0;
  top: 0;
  height: 6px;
  max-width: 100%;
  border-radius: 999px;
  background: var(--accent);
}

.metric-strip em {
  padding: 4px 7px;
  border-radius: 999px;
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.quality-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  align-items: start;
}

.audit-ledger-panel {
  grid-column: 1 / -1;
}

.audit-ledger-list {
  display: grid;
  gap: 12px;
}

.audit-ledger-list article {
  padding: 16px;
  border-left: 4px solid var(--accent);
}

.ledger-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
}

.ledger-head strong,
.ledger-head small {
  display: block;
}

.ledger-head strong {
  color: var(--vs-text);
  font-size: 15px;
}

.ledger-head small {
  margin-top: 5px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.ledger-head > span {
  flex: 0 0 auto;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--vs-card);
  font-size: 12px;
  font-weight: 900;
}

.audit-item-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.audit-item-grid > div {
  min-height: 128px;
  padding: 12px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
}

.audit-item-grid span {
  display: inline-flex;
  width: fit-content;
  padding: 4px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 900;
}

.audit-item-grid strong,
.audit-item-grid small,
.audit-item-grid em {
  display: block;
}

.audit-item-grid strong {
  margin-top: 8px;
  color: var(--vs-text);
  font-size: 13px;
}

.audit-item-grid small,
.audit-item-grid em {
  margin-top: 6px;
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: 1.45;
}

.audit-item-grid em {
  font-style: normal;
}

.missing-fields {
  margin: 14px 0 0;
  padding: 12px;
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-primary) 6%, var(--vs-card));
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.missing-fields b {
  color: var(--vs-text);
}

.issue-panel {
  display: grid;
  gap: 10px;
}

.issue-panel .state.small {
  min-height: 86px;
}

.issue-panel article {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.issue-panel article > span {
  display: inline-grid;
  width: 34px;
  height: 28px;
  place-items: center;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
}

.risk-high > span {
  background: rgba(207, 95, 95, 0.12);
  color: #bd4f4f;
}

.risk-mid > span {
  background: rgba(177, 132, 63, 0.14);
  color: #a06f2c;
}

.risk-low > span,
.risk-ok > span {
  background: rgba(47, 143, 107, 0.12);
  color: #2f8f6b;
}

.review-pill.risk-high,
.ledger-head > span.risk-high {
  background: rgba(207, 95, 95, 0.12);
  color: #bd4f4f;
}

.review-pill.risk-mid,
.ledger-head > span.risk-mid {
  background: rgba(177, 132, 63, 0.14);
  color: #a06f2c;
}

.review-pill.risk-low,
.review-pill.risk-ok,
.ledger-head > span.risk-low,
.ledger-head > span.risk-ok {
  background: rgba(47, 143, 107, 0.12);
  color: #2f8f6b;
}

.issue-panel strong,
.calibration-panel h3 {
  color: var(--vs-text);
}

.issue-panel strong,
.issue-panel small,
.issue-panel p,
.issue-panel em {
  display: block;
}

.issue-panel small {
  margin-top: 4px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.issue-panel p,
.issue-panel em,
.calibration-panel li {
  color: var(--vs-text-secondary);
  line-height: 1.6;
}

.issue-panel p {
  margin: 8px 0 0;
}

.issue-panel em {
  margin-top: 7px;
  font-size: 12px;
  font-style: normal;
}

.calibration-panel > span {
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.calibration-panel h3 {
  margin: 10px 0 0;
  font-size: 20px;
}

.calibration-panel ul {
  display: grid;
  gap: 10px;
  margin: 14px 0 0;
  padding-left: 18px;
}

.model-section {
  display: grid;
  gap: 20px;
  margin-top: 28px;
}

.model-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 22px;
  padding: 28px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-lg);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--vs-card) 94%, #3f7edb), var(--vs-card) 58%, color-mix(in srgb, var(--vs-card) 88%, #43a867));
  box-shadow: 0 18px 42px rgba(22, 49, 102, 0.07);
}

.model-hero h2 {
  margin: 0;
  color: var(--vs-text);
  font-size: clamp(28px, 3vw, 42px);
  line-height: 1.16;
}

.model-hero p {
  max-width: 820px;
  margin: 14px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.72;
}

.model-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.model-actions button {
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font-weight: 850;
}

.model-actions button.secondary {
  background: color-mix(in srgb, var(--vs-primary) 9%, var(--vs-card));
  color: var(--vs-primary);
}

.model-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.model-stats article,
.builder-panel,
.plan-panel,
.candidate-panel,
.candidate-card,
.training-panel,
.model-task-card,
.model-pipeline,
.quality-panel,
.model-readiness {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-card) 94%, transparent);
  box-shadow: 0 10px 26px rgba(29, 33, 41, 0.04);
}

.model-stats article {
  min-height: 118px;
  display: grid;
  align-content: center;
  gap: 6px;
  padding: 18px;
}

.model-stats strong {
  color: var(--vs-primary);
  font-size: 30px;
  line-height: 1;
}

.model-stats span {
  color: var(--vs-text);
  font-size: 14px;
  font-weight: 850;
}

.model-stats small {
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.model-builder {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.builder-panel,
.candidate-panel,
.training-panel,
.plan-panel {
  padding: 22px;
}

.section-head.compact button {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-primary);
  cursor: pointer;
  font-weight: 850;
}

.task-switcher {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.task-switcher button {
  min-height: 112px;
  padding: 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  cursor: pointer;
  text-align: left;
  font: inherit;
}

.task-switcher button.active,
.task-switcher button:hover {
  border-color: var(--vs-primary);
  background: color-mix(in srgb, var(--vs-primary) 8%, var(--vs-card));
}

.task-switcher strong,
.task-switcher span {
  display: block;
}

.task-switcher strong {
  color: var(--vs-text);
  font-size: 14px;
}

.task-switcher span {
  margin-top: 8px;
  line-height: 1.55;
  font-size: 12px;
}

.model-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.model-form-grid label {
  display: grid;
  gap: 7px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 850;
}

.model-form-grid input,
.model-form-grid select {
  width: 100%;
  height: 40px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text);
  font: inherit;
  padding: 0 10px;
}

.model-form-grid .check-line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.model-form-grid .check-line input {
  width: 16px;
  height: 16px;
}

.plan-panel > span {
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.plan-panel h3 {
  margin: 9px 0 0;
  color: var(--vs-text);
  font-size: 21px;
}

.plan-panel p {
  margin: 10px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.6;
}

.split-bars {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}

.split-bars div {
  padding: 12px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.split-bars strong,
.split-bars span {
  display: block;
}

.split-bars strong {
  color: var(--vs-primary);
  font-size: 20px;
}

.split-bars span {
  margin-top: 5px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.plan-panel dl {
  display: grid;
  gap: 9px;
  margin: 16px 0;
}

.plan-panel dl div {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.plan-panel dt,
.plan-panel dd {
  margin: 0;
  font-size: 13px;
}

.plan-panel dt {
  color: var(--vs-text-tertiary);
}

.plan-panel dd {
  color: var(--vs-text);
  font-weight: 850;
}

.plan-panel button {
  width: 100%;
  height: 40px;
  margin-top: 8px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 850;
}

.plan-panel button.secondary {
  background: color-mix(in srgb, var(--vs-primary) 8%, var(--vs-card));
  color: var(--vs-primary);
}

.plan-notice {
  padding: 10px 12px;
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, #43a867 12%, var(--vs-card));
  color: #24815b;
  font-size: 12px;
  font-weight: 850;
}

.candidate-panel .state.small {
  min-height: 92px;
}

.candidate-list {
  display: grid;
  gap: 10px;
}

.candidate-card {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-left: 4px solid var(--accent);
}

.candidate-card.selected {
  background: color-mix(in srgb, var(--accent) 7%, var(--vs-card));
  border-color: color-mix(in srgb, var(--accent) 56%, var(--vs-border));
  border-left-color: var(--accent);
}

.candidate-check {
  display: grid;
  justify-items: start;
  gap: 7px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.candidate-check input {
  width: 18px;
  height: 18px;
}

.candidate-check span {
  display: inline-flex;
  min-width: 52px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 12%, var(--vs-card));
  color: var(--accent);
  font-weight: 900;
}

.candidate-card h3 {
  margin: 0;
  color: var(--vs-text);
  font-size: 16px;
}

.candidate-card p {
  margin: 6px 0 0;
  color: var(--vs-text-secondary);
  font-size: 13px;
}

.candidate-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.candidate-tags em {
  padding: 4px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 10%, var(--vs-card));
  color: var(--accent);
  font-size: 12px;
  font-style: normal;
  font-weight: 850;
}

.candidate-card small {
  display: block;
  margin-top: 8px;
  color: var(--vs-text-tertiary);
  line-height: 1.45;
}

.candidate-card > button {
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--accent);
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--accent) 8%, var(--vs-card));
  color: var(--accent);
  cursor: pointer;
  font-weight: 850;
}

.training-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 10px;
}

.training-actions label {
  display: grid;
  gap: 6px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 850;
}

.training-actions input {
  width: 126px;
  height: 36px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text);
  padding: 0 10px;
  font: inherit;
}

.training-actions button {
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 850;
}

.training-actions button.secondary {
  background: color-mix(in srgb, var(--vs-primary) 8%, var(--vs-card));
  color: var(--vs-primary);
}

.training-actions button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.training-results {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.training-results article {
  min-height: 104px;
  padding: 16px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.training-results span,
.training-results strong,
.training-results small {
  display: block;
}

.training-results span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 850;
}

.training-results strong {
  margin-top: 7px;
  color: var(--vs-primary);
  font-size: 26px;
  line-height: 1;
}

.training-results small {
  margin-top: 8px;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.training-detail {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 12px;
  margin-top: 12px;
}

.training-detail > div,
.training-log {
  padding: 16px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.training-detail h3 {
  margin: 0 0 8px;
  color: var(--vs-text);
  font-size: 16px;
}

.training-detail p {
  margin: 7px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.58;
}

.training-detail .warning {
  color: #a36b24;
}

.feature-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.feature-list span {
  padding: 6px 9px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-primary) 8%, var(--vs-card));
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.feature-list strong {
  margin-left: 5px;
  color: var(--vs-primary);
}

.training-log {
  max-height: 170px;
  overflow: auto;
  margin-top: 12px;
}

.training-log span {
  display: block;
  margin-bottom: 8px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 900;
}

.training-log p {
  margin: 0;
  color: var(--vs-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 1.6;
}

.model-task-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.model-task-card {
  min-height: 250px;
  padding: 22px;
}

.model-task-card span,
.quality-panel > span {
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.model-task-card h3,
.quality-panel h3 {
  margin: 10px 0 0;
  color: var(--vs-text);
  font-size: 20px;
}

.model-task-card p {
  margin: 12px 0 16px;
  color: var(--vs-text-secondary);
  line-height: 1.68;
}

.model-task-card div {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.model-task-card em,
.readiness-row em {
  border-radius: 999px;
  background: color-mix(in srgb, #43a867 13%, var(--vs-card));
  color: #24815b;
  font-size: 12px;
  font-style: normal;
  font-weight: 850;
}

.model-task-card em {
  padding: 5px 9px;
}

.model-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}

.model-pipeline,
.quality-panel,
.model-readiness {
  padding: 22px;
}

.section-head.compact {
  margin-bottom: 16px;
}

.section-head.compact h2 {
  font-size: 21px;
}

.model-stage-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.model-stage-list article {
  min-height: 172px;
  padding: 16px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.model-stage-list i {
  display: inline-grid;
  width: 36px;
  height: 28px;
  place-items: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-primary) 12%, var(--vs-card));
  color: var(--vs-primary);
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
}

.model-stage-list strong {
  display: block;
  margin-top: 12px;
  color: var(--vs-text);
  font-size: 15px;
}

.model-stage-list p {
  margin: 9px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.62;
}

.quality-panel ul {
  display: grid;
  gap: 11px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.quality-panel li {
  position: relative;
  padding-left: 22px;
  color: var(--vs-text-secondary);
  line-height: 1.55;
}

.quality-panel li::before {
  content: "";
  position: absolute;
  left: 0;
  top: 8px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #43a867;
}

.readiness-table {
  overflow: hidden;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
}

.readiness-row {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(240px, 1.2fr) minmax(150px, 0.8fr) minmax(120px, 0.55fr) minmax(150px, 0.75fr) minmax(160px, 0.75fr);
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border: 0;
  border-bottom: 1px solid var(--vs-border);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  text-align: left;
  font: inherit;
}

.readiness-row:not(.head) {
  cursor: pointer;
}

.readiness-row:not(.head):hover {
  background: color-mix(in srgb, var(--vs-primary) 6%, var(--vs-card));
}

.readiness-row.head {
  background: var(--vs-card-soft);
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 900;
}

.readiness-row strong {
  color: var(--vs-text);
  font-size: 14px;
}

.readiness-row span {
  overflow-wrap: anywhere;
}

.readiness-row em {
  justify-self: start;
  padding: 5px 9px;
}

.readiness-row:last-child {
  border-bottom: 0;
}

.workflow-section {
  display: grid;
  gap: 20px;
  margin-top: 28px;
}

.workflow-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: 22px;
  padding: 28px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-lg);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--vs-card) 94%, #20a394), var(--vs-card) 58%, color-mix(in srgb, var(--vs-card) 88%, #3f7edb));
  box-shadow: 0 18px 42px rgba(22, 49, 102, 0.07);
}

.workflow-hero h2 {
  margin: 0;
  color: var(--vs-text);
  font-size: clamp(28px, 3vw, 42px);
  line-height: 1.16;
}

.workflow-hero p {
  max-width: 820px;
  margin: 14px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.72;
}

.workflow-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.workflow-actions button {
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font-weight: 850;
}

.workflow-actions button.secondary {
  background: color-mix(in srgb, var(--vs-primary) 9%, var(--vs-card));
  color: var(--vs-primary);
}

.workflow-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.workflow-metrics article,
.workflow-stage,
.queue-panel,
.artifact-panel,
.workflow-rules {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-card) 94%, transparent);
  box-shadow: 0 10px 26px rgba(29, 33, 41, 0.04);
}

.workflow-metrics article {
  min-height: 118px;
  display: grid;
  align-content: center;
  gap: 6px;
  padding: 18px;
}

.workflow-metrics strong {
  color: var(--vs-primary);
  font-size: 30px;
  line-height: 1;
}

.workflow-metrics span {
  color: var(--vs-text);
  font-size: 14px;
  font-weight: 850;
}

.workflow-metrics small {
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.workflow-flow {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.workflow-stage {
  position: relative;
  min-height: 210px;
  padding: 18px;
  overflow: hidden;
}

.workflow-stage::after {
  content: "";
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 0;
  height: 4px;
  border-radius: 999px 999px 0 0;
  background: var(--vs-primary);
  opacity: 0.72;
}

.workflow-stage i {
  display: inline-grid;
  width: 38px;
  height: 30px;
  place-items: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-primary) 12%, var(--vs-card));
  color: var(--vs-primary);
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
}

.workflow-stage > span {
  float: right;
  padding: 5px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, #43a867 12%, var(--vs-card));
  color: #24815b;
  font-size: 11px;
  font-weight: 900;
}

.workflow-stage h3 {
  margin: 18px 0 0;
  color: var(--vs-text);
  font-size: 17px;
}

.workflow-stage p {
  margin: 10px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.62;
}

.workflow-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}

.queue-panel,
.artifact-panel,
.workflow-rules {
  padding: 22px;
}

.queue-list {
  display: grid;
  gap: 10px;
}

.queue-list article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.queue-list strong,
.queue-list span {
  display: block;
}

.queue-list strong {
  color: var(--vs-text);
  font-size: 15px;
}

.queue-list span {
  margin-top: 5px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.queue-list em {
  min-width: 70px;
  padding: 6px 9px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
  color: var(--vs-primary);
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
  text-align: center;
}

.queue-list em.done {
  background: color-mix(in srgb, #43a867 13%, var(--vs-card));
  color: #24815b;
}

.queue-list em.pending {
  background: color-mix(in srgb, #c9992e 14%, var(--vs-card));
  color: #8a661b;
}

.queue-list b {
  min-width: 72px;
  color: var(--vs-text);
  font-size: 18px;
  text-align: right;
}

.artifact-panel > span {
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.artifact-panel h3 {
  margin: 10px 0 0;
  color: var(--vs-text);
  font-size: 20px;
}

.artifact-panel ul {
  display: grid;
  gap: 10px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.artifact-panel li {
  position: relative;
  padding-left: 22px;
  color: var(--vs-text-secondary);
  line-height: 1.55;
}

.artifact-panel li::before {
  content: "";
  position: absolute;
  left: 0;
  top: 8px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--vs-primary);
}

.rule-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.rule-grid article {
  min-height: 138px;
  padding: 16px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.rule-grid strong {
  color: var(--vs-text);
  font-size: 15px;
}

.rule-grid p {
  margin: 9px 0 0;
  color: var(--vs-text-secondary);
  line-height: 1.6;
}

@media (max-width: 1420px) {
  .dashboard-hero {
    grid-template-columns: minmax(280px, 0.88fr) minmax(400px, 1fr) 220px;
  }

  .hero {
    grid-template-columns: minmax(0, 1fr) 320px;
  }

  .hero-metrics {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .insight-grid {
    grid-template-columns: 1fr 1.4fr;
  }

  .concern-grid,
  .evidence-flow,
  .demo-grid,
  .quality-summary,
  .quality-gates,
  .audit-flow,
  .audit-rule-grid,
  .audit-item-grid,
  .model-form-grid,
  .training-results,
  .training-detail,
  .workflow-flow,
  .rule-grid,
  .model-stage-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .element-board {
    grid-column: 1 / -1;
  }
}

@media (min-width: 2200px) {
  .shell {
    width: min(2120px, calc(100% - 96px));
  }

  .dashboard-hero {
    grid-template-columns: minmax(420px, 0.9fr) minmax(700px, 1.18fr) 280px;
    min-height: 350px;
  }

  .dashboard-scene-panel {
    min-height: 292px;
  }
}

@media (max-width: 1280px) {
  .dataset-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-hero {
    grid-template-columns: minmax(0, 1fr) minmax(320px, 0.8fr);
  }

  .dashboard-kpis {
    grid-column: 1 / -1;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .dashboard-overview {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 980px) {
  .shell {
    width: min(100% - 40px, 920px);
  }

  .hero,
  .dashboard-hero,
  .insight-grid,
  .dashboard-overview,
  .quality-grid,
  .model-hero,
  .model-builder,
  .model-workspace,
  .preflight-grid,
  .workflow-hero,
  .workflow-grid {
    grid-template-columns: 1fr;
  }

  .review-header {
    align-items: stretch;
    flex-direction: column;
  }

  .concern-grid,
  .evidence-flow,
  .demo-grid,
  .quality-summary,
  .quality-gates,
  .audit-flow,
  .audit-status-panel,
  .audit-rule-grid,
  .audit-item-grid,
  .publication-list,
  .task-switcher,
  .model-form-grid,
  .training-results,
  .training-detail,
  .workflow-flow,
  .rule-grid,
  .model-task-grid,
  .model-stage-list {
    grid-template-columns: 1fr;
  }

  .hero-visual {
    min-height: 260px;
  }

  .dashboard-scene-panel {
    min-height: 230px;
  }

  .domain-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .quality-hero {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .shell {
    width: calc(100% - 32px);
    padding: 26px 0 42px;
  }

  .hero {
    padding: 24px;
  }

  .dashboard-hero {
    padding: 18px;
  }

  h1 {
    font-size: 32px;
  }

  .section-head {
    display: block;
  }

  .hero-metrics,
  .dashboard-kpis,
  .domain-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .element-bars {
    grid-template-columns: repeat(9, minmax(0, 1fr));
    height: auto;
  }

  .model-stats {
    grid-template-columns: 1fr;
  }

  .workflow-metrics {
    grid-template-columns: 1fr;
  }

  .quality-summary,
  .quality-gates {
    grid-template-columns: 1fr;
  }

  .readiness-table {
    display: grid;
    gap: 10px;
    border: 0;
    border-radius: 0;
    overflow: visible;
  }

  .readiness-row,
  .readiness-row.head {
    grid-template-columns: 1fr;
    border: 1px solid var(--vs-border);
    border-radius: var(--vs-radius-sm);
  }

  .readiness-row.head {
    display: none;
  }

  .candidate-card {
    grid-template-columns: 1fr;
  }

  .candidate-card > button {
    justify-self: start;
  }

  .dataset-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .hero {
    padding: 22px;
  }

  .dashboard-kpis {
    grid-template-columns: 1fr;
  }

  h1 {
    font-size: 30px;
  }

  .hero-metrics,
  .dashboard-kpis,
  .stats,
  .domain-list {
    grid-template-columns: 1fr;
  }

  .dataset-card {
    min-height: 0;
    padding: 20px;
  }

  .card-top {
    grid-template-columns: auto 1fr;
  }

  .pill {
    grid-column: 1 / -1;
    max-width: 100%;
  }
}
</style>
