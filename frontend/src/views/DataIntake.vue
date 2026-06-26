<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTopbar from '../components/AppTopbar.vue'
import {
  adaptDatasetSubmission,
  fetchDiscoveryCandidates,
  fetchDiscoveryConfig,
  fetchDiscoveryRuns,
  fetchDiscoverySources,
  fetchDatasetSubmissions,
  fetchIngestSuggestion,
  fetchMe,
  fetchPublicSources,
  ingestSubmission,
  prepareDatasetPipeline,
  promoteDiscoveryCandidate,
  reviewDiscoveryCandidate,
  reviewDatasetSubmission,
  runDatasetDiscovery,
  saveDatasetSubmissionSource,
  submitDatasetSource,
  updateDiscoveryConfig,
  validateDiscoveryCandidate,
  withdrawSubmission as withdrawSubmissionApi
} from '../api'
import { authUser, isAdminOrSuperAdmin, isAuthenticated, isSuperAdmin } from '../auth/session'

const router = useRouter()
const sources = ref([])
const discoverySources = ref([])
const discoveryRuns = ref([])
const discoveryCandidates = ref([])
const discoveryConfig = ref(null)
const editingConfig = ref(false)
const configEdit = reactive({})
const submissions = ref([])
const loading = ref(true)
const working = ref(false)
const discoveryWorking = ref(false)
const error = ref('')
const notice = ref('')
const reviewNotes = reactive({})
const discoveryNotes = reactive({})
const ingestState = reactive({})
const activeAdaptId = ref(null)
const activeSaveId = ref(null)
const adaptProgress = reactive({})
const form = reactive({
  datasetName: '',
  dataType: '',
  description: '',
  paperUrl: '',
  dataUrl: '',
  dataFormat: '',
  license: '',
  providedFields: ''
})

const pipelineSteps = [
  { key: 'SUBMITTED', label: '来源提交', note: '论文 DOI、数据地址与字段说明' },
  { key: 'SOURCE_APPROVED', label: '来源审核', note: '许可、可信度和重复性审查' },
  { key: 'ADAPTER_REQUIRED', label: '格式适配', note: '解析器、字段映射与单位校验' },
  { key: 'VALIDATED', label: '入库校验', note: '记录数、抽样结构与元数据检查' },
  { key: 'PUBLISHED', label: '发布更新', note: '写入展示库并刷新目录' }
]

const adaptSteps = [
  { key: 'guard', label: '安全校验', pct: 15, detail: '校验下载链接是否为安全的公开 HTTP/HTTPS 地址。' },
  { key: 'download', label: '读取样本', pct: 38, detail: '从远程链接读取样本；大文件只抽取前若干 MB。' },
  { key: 'detect', label: '识别格式', pct: 62, detail: '识别 CSV/TSV、JSON/JSONL、HDF5、XYZ、CIF、POSCAR 或压缩归档格式。' },
  { key: 'parse', label: '解析预检', pct: 84, detail: '提取字段、结构、记录估计和科学属性线索。' },
  { key: 'save', label: '写入结果', pct: 100, detail: '保存预检报告并刷新接入任务。' }
]

const submissionHeading = computed(() => isSuperAdmin.value ? '待审核与管线任务' : '我的投稿进度')
const canViewDiscoveryReview = computed(() => isAdminOrSuperAdmin.value)
const candidateStats = computed(() => {
  const rows = discoveryCandidates.value
  return [
    { label: '候选数据集', value: rows.length },
    { label: '高分候选', value: rows.filter(item => item.score >= 80).length },
    { label: '待审核', value: rows.filter(item => ['CANDIDATE', 'REVIEW'].includes(item.status)).length },
    { label: '已转入', value: rows.filter(item => item.status === 'APPROVED').length }
  ]
})

const intakeQueueStats = computed(() => {
  const rows = submissions.value
  return [
    { label: '待来源审核', value: rows.filter(item => item.status === 'SUBMITTED').length },
    { label: '已批准来源', value: rows.filter(item => item.status === 'APPROVED').length },
    { label: '待适配器开发', value: rows.filter(item => item.pipelineStage === 'ADAPTER_REQUIRED').length },
    { label: '已退回', value: rows.filter(item => item.status === 'REJECTED').length }
  ]
})

const adminPipelineGuide = [
  { title: '1. 核验来源', text: '确认 DOI、论文页、数据下载页、许可证和重复数据风险。' },
  { title: '2. 批准进入队列', text: '批准后数据只进入接入队列，不会自动发布到数据中心。' },
  { title: '3. 开发解析适配器', text: '根据 HDF5、CIF、XYZ、JSON/JSONL、CSV/TSV、压缩包、LMDB 等格式编写字段映射和抽样校验。' },
  { title: '4. 重建展示库', text: '运行构建脚本写入 H2 展示库，再执行质量验证和发布门控。' }
]

onMounted(loadPage)

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    sources.value = await fetchPublicSources()
    discoverySources.value = await fetchDiscoverySources()
    if (isAuthenticated.value) {
      await fetchMe()
    }
    if (isAuthenticated.value) {
      submissions.value = await fetchDatasetSubmissions()
    }
    if (canViewDiscoveryReview.value) {
      discoveryCandidates.value = await fetchDiscoveryCandidates()
      discoveryRuns.value = await fetchDiscoveryRuns()
      discoveryConfig.value = await fetchDiscoveryConfig()
    }
  } catch (err) {
    error.value = err.message || '数据接入信息加载失败'
  } finally {
    loading.value = false
  }
}

async function runDiscoveryNow() {
  error.value = ''
  notice.value = ''
  discoveryWorking.value = true
  try {
    const run = await runDatasetDiscovery()
    notice.value = run.status === 'RUNNING'
      ? '自动发现任务已启动：后台检索完成后会自动分诊（高分转入审核队列、低分归档），稍后刷新即可看到运行结果。'
      : `自动发现完成：发现 ${run.discovered}，新增 ${run.inserted}，自动转入 ${run.autoPromoted}，自动归档 ${run.autoArchived}。`
    discoveryCandidates.value = await fetchDiscoveryCandidates()
    discoveryRuns.value = await fetchDiscoveryRuns()
  } catch (err) {
    error.value = err.message || '自动发现运行失败'
  } finally {
    discoveryWorking.value = false
  }
}

async function reviewCandidate(candidate, decision) {
  error.value = ''
  notice.value = ''
  working.value = true
  try {
    await reviewDiscoveryCandidate(candidate.id, decision, discoveryNotes[candidate.id] || '')
    discoveryCandidates.value = await fetchDiscoveryCandidates()
    notice.value = '候选状态已更新。'
  } catch (err) {
    error.value = err.message || '候选审核失败'
  } finally {
    working.value = false
  }
}

async function promoteCandidate(candidate) {
  error.value = ''
  notice.value = ''
  working.value = true
  try {
    await promoteDiscoveryCandidate(candidate.id)
    discoveryCandidates.value = await fetchDiscoveryCandidates()
    submissions.value = await fetchDatasetSubmissions()
    notice.value = '候选已转入数据接入审核队列。'
  } catch (err) {
    error.value = err.message || '转入接入队列失败'
  } finally {
    working.value = false
  }
}

function startEditConfig() {
  const c = discoveryConfig.value
  if (!c) return
  Object.assign(configEdit, {
    enabled: c.enabled,
    datacite: c.datacite,
    zenodo: c.zenodo,
    figshare: c.figshare,
    dryad: c.dryad,
    openaire: c.openaire,
    nomad: c.nomad,
    validateEnabled: c.validateEnabled,
    maxResultsPerQuery: c.maxResultsPerQuery,
    queriesText: (c.queries || []).join('\n'),
    autoPromoteEnabled: c.autoPromoteEnabled,
    autoPromoteMinScore: c.autoPromoteMinScore,
    autoPromoteMinRelevance: c.autoPromoteMinRelevance,
    autoPromoteRequireLicense: c.autoPromoteRequireLicense,
    autoPromoteRequireValidationPass: c.autoPromoteRequireValidationPass,
    autoPromoteMaxPerRun: c.autoPromoteMaxPerRun,
    autoArchiveEnabled: c.autoArchiveEnabled,
    autoArchiveMaxScore: c.autoArchiveMaxScore,
    autoAdaptEnabled: c.autoAdaptEnabled,
    autoAdaptMaxDownloadMb: c.autoAdaptMaxDownloadMb,
    autoAdaptSampleMb: c.autoAdaptSampleMb
  })
  editingConfig.value = true
}

function cancelEditConfig() {
  editingConfig.value = false
}

async function saveConfig() {
  error.value = ''
  notice.value = ''
  working.value = true
  try {
    const payload = {
      enabled: configEdit.enabled,
      datacite: configEdit.datacite,
      zenodo: configEdit.zenodo,
      figshare: configEdit.figshare,
      dryad: configEdit.dryad,
      openaire: configEdit.openaire,
      nomad: configEdit.nomad,
      validateEnabled: configEdit.validateEnabled,
      maxResultsPerQuery: Number(configEdit.maxResultsPerQuery),
      queries: String(configEdit.queriesText || '').split('\n').map(q => q.trim()).filter(Boolean),
      autoPromoteEnabled: configEdit.autoPromoteEnabled,
      autoPromoteMinScore: Number(configEdit.autoPromoteMinScore),
      autoPromoteMinRelevance: Number(configEdit.autoPromoteMinRelevance),
      autoPromoteRequireLicense: configEdit.autoPromoteRequireLicense,
      autoPromoteRequireValidationPass: configEdit.autoPromoteRequireValidationPass,
      autoPromoteMaxPerRun: Number(configEdit.autoPromoteMaxPerRun),
      autoArchiveEnabled: configEdit.autoArchiveEnabled,
      autoArchiveMaxScore: Number(configEdit.autoArchiveMaxScore),
      autoAdaptEnabled: configEdit.autoAdaptEnabled,
      autoAdaptMaxDownloadMb: Number(configEdit.autoAdaptMaxDownloadMb),
      autoAdaptSampleMb: Number(configEdit.autoAdaptSampleMb)
    }
    discoveryConfig.value = await updateDiscoveryConfig(payload)
    editingConfig.value = false
    notice.value = '发现配置已保存并即时生效。'
  } catch (err) {
    error.value = err.message || '保存发现配置失败'
  } finally {
    working.value = false
  }
}

async function revalidateCandidate(candidate) {
  error.value = ''
  notice.value = ''
  working.value = true
  try {
    await validateDiscoveryCandidate(candidate.id)
    discoveryCandidates.value = await fetchDiscoveryCandidates()
    notice.value = '已重新校对该候选。'
  } catch (err) {
    error.value = err.message || '候选校对失败'
  } finally {
    working.value = false
  }
}

async function adaptSubmission(submission) {
  error.value = ''
  notice.value = ''
  working.value = true
  activeAdaptId.value = submission.id
  let stepIndex = 0
  adaptProgress[submission.id] = {
    status: 'running',
    percent: adaptSteps[0].pct,
    label: adaptSteps[0].label,
    detail: adaptSteps[0].detail
  }
  const timer = window.setInterval(() => {
    if (stepIndex < adaptSteps.length - 2) {
      stepIndex += 1
      const step = adaptSteps[stepIndex]
      adaptProgress[submission.id] = {
        status: 'running',
        percent: step.pct,
        label: step.label,
        detail: step.detail
      }
    }
  }, 900)
  try {
    await adaptDatasetSubmission(submission.id)
    const finalStep = adaptSteps[adaptSteps.length - 1]
    adaptProgress[submission.id] = {
      status: 'done',
      percent: finalStep.pct,
      label: '预检完成',
      detail: '已保存自动解析预检结果，见下方报告。'
    }
    submissions.value = await fetchDatasetSubmissions()
    notice.value = '已读取远程样本并完成解析预检，可在该任务下查看格式、摘要和样本信息。'
  } catch (err) {
    adaptProgress[submission.id] = {
      status: 'failed',
      percent: 100,
      label: '预检失败',
      detail: err.message || '自动解析预检失败'
    }
    error.value = err.message || '自动解析预检失败'
  } finally {
    window.clearInterval(timer)
    activeAdaptId.value = null
    working.value = false
  }
}

async function saveSubmissionSource(submission) {
  if (!window.confirm('确认将该来源链接保存到本地？大文件会占用 documents/data/intake_downloads 空间。')) {
    return
  }
  error.value = ''
  notice.value = ''
  working.value = true
  activeSaveId.value = submission.id
  try {
    await saveDatasetSubmissionSource(submission.id)
    submissions.value = await fetchDatasetSubmissions()
    notice.value = '源文件已保存到本地，可在该任务的预检结果中查看路径、大小和 SHA-256。'
  } catch (err) {
    error.value = err.message || '保存源文件失败'
  } finally {
    activeSaveId.value = null
    working.value = false
  }
}

function parsedProfile(submission) {
  if (!submission || !submission.parseProfile) return null
  try {
    return JSON.parse(submission.parseProfile)
  } catch {
    return null
  }
}

function inferDoi(submission) {
  const text = [submission?.paperUrl, submission?.dataUrl, submission?.description]
    .filter(Boolean)
    .join(' ')
  const match = text.match(/10\.\d{4,9}\/[-._;()/:A-Z0-9]+/i)
  return match ? match[0].replace(/[.,;，。；]+$/, '') : ''
}

function shortUrl(url) {
  if (!url) return '未提供'
  try {
    const parsed = new URL(url)
    const path = parsed.pathname.replace(/\/$/, '')
    const shortPath = path.length > 34 ? `${path.slice(0, 18)}...${path.slice(-12)}` : path
    return `${parsed.hostname}${shortPath}`
  } catch {
    return url.length > 46 ? `${url.slice(0, 28)}...${url.slice(-12)}` : url
  }
}

function adaptButtonText(submission) {
  if (activeAdaptId.value === submission.id) {
    return adaptProgress[submission.id]?.label || '预检中...'
  }
  return '读取样本并解析预检'
}

function adaptStepState(progress, step) {
  if (!progress) return ''
  if (progress.status === 'failed') return 'blocked'
  if (progress.percent >= step.pct) return 'done'
  const current = adaptSteps.find(item => item.pct >= progress.percent)
  return current?.key === step.key ? 'active' : ''
}

function formatBytes(value) {
  const n = Number(value || 0)
  if (!Number.isFinite(n) || n <= 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = n
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size >= 10 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`
}

async function loadIngest(submission) {
  error.value = ''
  notice.value = ''
  working.value = true
  try {
    const suggestion = await fetchIngestSuggestion(submission.id)
    ingestState[submission.id] = {
      suggestion,
      datasetName: suggestion.datasetName,
      mappingText: JSON.stringify(suggestion.suggestedMapping || {}, null, 2),
      limit: 200
    }
  } catch (err) {
    error.value = err.message || '获取入库建议失败'
  } finally {
    working.value = false
  }
}

async function withdrawSubmission(submission) {
  if (!window.confirm(`确认撤回入库？将从展示库删除数据集 intake_${submission.id} 的全部记录。`)) {
    return
  }
  error.value = ''
  notice.value = ''
  working.value = true
  try {
    const res = await withdrawSubmissionApi(submission.id)
    submissions.value = await fetchDatasetSubmissions()
    notice.value = res.pipelineMessage || '已撤回入库。'
  } catch (err) {
    error.value = err.message || '撤回入库失败'
  } finally {
    working.value = false
  }
}

async function doIngest(submission) {
  const state = ingestState[submission.id]
  if (!state) return
  let mapping
  try {
    mapping = JSON.parse(state.mappingText || '{}')
  } catch {
    error.value = '字段映射不是合法 JSON'
    return
  }
  error.value = ''
  notice.value = ''
  working.value = true
  try {
    const res = await ingestSubmission(submission.id, {
      datasetName: state.datasetName,
      mapping,
      limit: Number(state.limit) || 200
    })
    submissions.value = await fetchDatasetSubmissions()
    notice.value = res.pipelineMessage || '入库完成。'
    ingestState[submission.id] = null
  } catch (err) {
    error.value = err.message || '入库失败'
  } finally {
    working.value = false
  }
}

function backHome() {
  router.push({ name: 'home' })
}

function openLogin() {
  router.push({ name: 'login', query: { redirect: '/intake' } })
}

async function submitProposal() {
  notice.value = ''
  error.value = ''
  if (!form.description.trim()) {
    error.value = '请先填写数据集说明'
    return
  }
  if (!form.paperUrl.trim() && !form.dataUrl.trim()) {
    error.value = '请至少填写一个论文链接或数据链接'
    return
  }
  working.value = true
  try {
    await submitDatasetSource({ ...form })
    Object.keys(form).forEach(key => {
      form[key] = ''
    })
    notice.value = '数据集来源已提交，超级管理员审核后会进入接入管线。'
    submissions.value = await fetchDatasetSubmissions()
  } catch (err) {
    error.value = err.message || '提交失败'
  } finally {
    working.value = false
  }
}

async function review(submission, decision) {
  error.value = ''
  working.value = true
  try {
    await reviewDatasetSubmission(submission.id, decision, reviewNotes[submission.id] || '')
    submissions.value = await fetchDatasetSubmissions()
  } catch (err) {
    error.value = err.message || '审核失败'
  } finally {
    working.value = false
  }
}

async function prepare(submission) {
  error.value = ''
  notice.value = ''
  working.value = true
  try {
    await prepareDatasetPipeline(submission.id)
    submissions.value = await fetchDatasetSubmissions()
    notice.value = '已转入格式适配阶段。下一步需要管理员开发解析适配器、字段映射和入库校验脚本。'
  } catch (err) {
    error.value = err.message || '推进管线失败'
  } finally {
    working.value = false
  }
}

function activeStepIndex(submission) {
  if (submission.status === 'REJECTED' || submission.pipelineStage === 'CLOSED') return 0
  const index = pipelineSteps.findIndex(step => step.key === submission.pipelineStage)
  return index < 0 ? 0 : index
}

function stepState(submission, index) {
  if (submission.status === 'REJECTED' || submission.pipelineStage === 'CLOSED') {
    return index === 0 ? 'blocked' : ''
  }
  const active = activeStepIndex(submission)
  if (index < active) return 'done'
  if (index === active) return 'active'
  return ''
}

function adminNextAction(submission) {
  if (submission.status === 'SUBMITTED') {
    return '等待超级管理员核验来源真实性、许可证、论文与数据链接。通过后点击“批准来源”。'
  }
  if (submission.status === 'REJECTED' || submission.pipelineStage === 'CLOSED') {
    return '该申请已退回；如来源信息补充完整，需要用户重新提交或管理员重新创建接入申请。'
  }
  if (submission.pipelineStage === 'SOURCE_APPROVED') {
    return '来源已批准。下一步点击“进入格式适配阶段”，将任务转入解析适配器开发。'
  }
  if (submission.pipelineStage === 'ADAPTER_REQUIRED') {
    return '当前需要人工开发或配置解析适配器：下载小样本，确认字段映射、单位、结构坐标，再更新构建脚本并重建 H2 展示库。'
  }
  if (submission.pipelineStage === 'VALIDATED') {
    return '已完成入库校验。下一步由超级管理员决定是否发布到数据中心。'
  }
  if (submission.pipelineStage === 'PUBLISHED') {
    return '已发布。后续只需维护版本、引用、质量问题和更新日志。'
  }
  return submission.pipelineMessage || '等待管理员补充处理说明。'
}

function statusLabel(status) {
  return {
    SUBMITTED: '待审核',
    APPROVED: '已批准',
    REJECTED: '已退回'
  }[status] || status
}

function stageLabel(stage) {
  return pipelineSteps.find(step => step.key === stage)?.label || (stage === 'CLOSED' ? '已关闭' : stage)
}

function discoveryStatusLabel(status) {
  return {
    CANDIDATE: '候选',
    REVIEW: '复核',
    APPROVED: '已转入',
    REJECTED: '已拒绝',
    ARCHIVED: '已归档'
  }[status] || status
}

function scoreClass(score) {
  if (score >= 80) return 'high'
  if (score >= 60) return 'mid'
  return 'low'
}

function relevanceClass(relevance) {
  if (relevance >= 66) return 'high'
  if (relevance >= 44) return 'mid'
  return 'low'
}

function runStatusClass(status) {
  return {
    SUCCESS: 'ok',
    RUNNING: 'running',
    PARTIAL: 'warn',
    FAILED: 'bad'
  }[status] || 'running'
}

function sourceStatClass(status) {
  return {
    OK: 'ok',
    EMPTY: 'muted',
    DISABLED: 'muted',
    ERROR: 'bad'
  }[status] || 'muted'
}

function validationClass(status) {
  return {
    PASS: 'ok',
    WARN: 'warn',
    FAIL: 'bad'
  }[status] || 'muted'
}

function validationLabel(status) {
  return {
    PASS: '校对通过',
    WARN: '校对提醒',
    FAIL: '校对未过'
  }[status] || '未校对'
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
}
</script>

<template>
  <div class="page">
    <AppTopbar show-back @brand-click="backHome" />

    <main class="shell">
      <header class="page-head">
        <div>
          <p class="eyebrow">Dataset intake</p>
          <h1>数据接入中心</h1>
          <p>管理公开数据来源、论文附属数据投稿与受控发布流程。新的异构数据只有经过审核、解析适配和校验后才会进入展示库。</p>
        </div>
        <div class="head-stat">
          <strong>{{ sources.length }}</strong>
          <span>候选公开来源</span>
        </div>
      </header>

      <p v-if="error" class="feedback error">{{ error }}</p>
      <p v-if="notice" class="feedback success">{{ notice }}</p>

      <section class="discovery-panel">
        <div class="section-title">
          <div>
            <h2>自动发现中心</h2>
            <p>系统按计划自动检索公开数据源，完成链接预检、格式/字段识别与质量评分，并把高置信候选自动转入接入审核队列、低分线索自动归档；管理员只做最终发布审核。</p>
          </div>
          <button
            v-if="isSuperAdmin"
            type="button"
            class="run-discovery"
            :disabled="discoveryWorking"
            @click="runDiscoveryNow"
          >
            {{ discoveryWorking ? '发现中...' : '立即发现' }}
          </button>
        </div>

        <div v-if="canViewDiscoveryReview" class="discovery-stats">
          <article v-for="item in candidateStats" :key="item.label">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </article>
        </div>

        <div class="connector-grid">
          <article v-for="source in discoverySources" :key="source.key">
            <div>
              <strong>{{ source.name }}</strong>
              <span>{{ source.status }}</span>
            </div>
            <p>{{ source.capability }}</p>
            <small>{{ source.cadence }}</small>
          </article>
        </div>

        <div v-if="canViewDiscoveryReview && discoveryConfig" class="config-panel">
          <div class="config-head">
            <strong>当前发现配置</strong>
            <span v-if="!editingConfig">{{ isSuperAdmin ? '超级管理员可在线编辑（即时生效并持久化）' : '只读 · 由超级管理员维护' }}</span>
            <span v-else>编辑中 · cron/联系邮箱仍由 application.yml 控制</span>
            <button v-if="isSuperAdmin && !editingConfig" type="button" class="cfg-edit" @click="startEditConfig">编辑配置</button>
          </div>
          <div v-if="!editingConfig" class="config-grid">
            <article>
              <span>调度</span>
              <strong>{{ discoveryConfig.enabled ? '已启用' : '已停用' }}</strong>
              <small>cron {{ discoveryConfig.cron }}</small>
            </article>
            <article>
              <span>数据源</span>
              <strong>{{ [discoveryConfig.datacite ? 'DataCite' : null, discoveryConfig.zenodo ? 'Zenodo' : null, discoveryConfig.figshare ? 'Figshare' : null, discoveryConfig.dryad ? 'Dryad' : null, discoveryConfig.openaire ? 'OpenAIRE' : null, discoveryConfig.nomad ? 'NOMAD' : null].filter(Boolean).join(' · ') || '全部关闭' }}</strong>
              <small>每查询取 {{ discoveryConfig.maxResultsPerQuery }} 条</small>
            </article>
            <article>
              <span>自动校对</span>
              <strong>{{ discoveryConfig.validateEnabled ? '开' : '关' }}</strong>
              <small>发现即校对 · {{ discoveryConfig.autoPromoteRequireValidationPass ? '转入需校对 PASS' : 'FAIL 阻止自动转入' }}</small>
            </article>
            <article>
              <span>自动转入</span>
              <strong>{{ discoveryConfig.autoPromoteEnabled ? '开' : '关' }}</strong>
              <small>≥{{ discoveryConfig.autoPromoteMinScore }} 分 · 相关度≥{{ discoveryConfig.autoPromoteMinRelevance }}{{ discoveryConfig.autoPromoteRequireLicense ? ' · 需许可证' : '' }} · 每轮≤{{ discoveryConfig.autoPromoteMaxPerRun }}</small>
            </article>
            <article>
              <span>自动归档</span>
              <strong>{{ discoveryConfig.autoArchiveEnabled ? '开' : '关' }}</strong>
              <small>≤{{ discoveryConfig.autoArchiveMaxScore }} 分</small>
            </article>
            <article>
              <span>自动解析下载</span>
              <strong>{{ discoveryConfig.autoAdaptEnabled ? '开' : '关' }}</strong>
              <small>整文件≤{{ discoveryConfig.autoAdaptMaxDownloadMb }}MB · 大文件抽样{{ discoveryConfig.autoAdaptSampleMb }}MB</small>
            </article>
          </div>
          <div v-if="!editingConfig" class="config-queries">
            <span>检索关键词</span>
            <div>
              <em v-for="q in discoveryConfig.queries" :key="q">{{ q }}</em>
            </div>
          </div>

          <form v-if="editingConfig" class="config-form" @submit.prevent="saveConfig">
            <div class="cfg-row">
              <label class="chk"><input type="checkbox" v-model="configEdit.enabled"> 启用调度</label>
              <label class="chk"><input type="checkbox" v-model="configEdit.validateEnabled"> 自动校对</label>
            </div>
            <div class="cfg-row">
              <span class="cfg-label">数据源</span>
              <label class="chk"><input type="checkbox" v-model="configEdit.datacite"> DataCite</label>
              <label class="chk"><input type="checkbox" v-model="configEdit.zenodo"> Zenodo</label>
              <label class="chk"><input type="checkbox" v-model="configEdit.figshare"> Figshare</label>
              <label class="chk"><input type="checkbox" v-model="configEdit.dryad"> Dryad</label>
              <label class="chk"><input type="checkbox" v-model="configEdit.openaire"> OpenAIRE</label>
              <label class="chk"><input type="checkbox" v-model="configEdit.nomad"> NOMAD</label>
              <label class="num">每查询条数<input type="number" min="1" max="50" v-model.number="configEdit.maxResultsPerQuery"></label>
            </div>
            <label class="cfg-block">检索关键词（每行一个，最多 20 条）
              <textarea v-model="configEdit.queriesText" rows="5" placeholder="VASP DFT dataset energy force materials"></textarea>
            </label>
            <div class="cfg-row">
              <label class="chk"><input type="checkbox" v-model="configEdit.autoPromoteEnabled"> 自动转入</label>
              <label class="num">最低分<input type="number" min="0" max="100" v-model.number="configEdit.autoPromoteMinScore"></label>
              <label class="num">最低相关度<input type="number" min="0" max="100" v-model.number="configEdit.autoPromoteMinRelevance"></label>
              <label class="num">每轮上限<input type="number" min="1" max="100" v-model.number="configEdit.autoPromoteMaxPerRun"></label>
            </div>
            <div class="cfg-row">
              <label class="chk"><input type="checkbox" v-model="configEdit.autoPromoteRequireLicense"> 转入需许可证</label>
              <label class="chk"><input type="checkbox" v-model="configEdit.autoPromoteRequireValidationPass"> 转入需校对 PASS</label>
            </div>
            <div class="cfg-row">
              <label class="chk"><input type="checkbox" v-model="configEdit.autoArchiveEnabled"> 自动归档</label>
              <label class="num">归档阈值≤<input type="number" min="0" max="100" v-model.number="configEdit.autoArchiveMaxScore"></label>
            </div>
            <div class="cfg-row">
              <label class="chk"><input type="checkbox" v-model="configEdit.autoAdaptEnabled"> 自动解析下载</label>
              <label class="num">整文件上限MB<input type="number" min="1" max="4096" v-model.number="configEdit.autoAdaptMaxDownloadMb"></label>
              <label class="num">大文件抽样MB<input type="number" min="1" max="256" v-model.number="configEdit.autoAdaptSampleMb"></label>
            </div>
            <div class="cfg-actions">
              <button type="submit" :disabled="working">保存并生效</button>
              <button type="button" class="ghost" :disabled="working" @click="cancelEditConfig">取消</button>
            </div>
          </form>
        </div>

        <div v-if="canViewDiscoveryReview" class="discovery-workspace">
          <section>
            <div class="section-title compact">
              <h3>候选数据集</h3>
              <span>按评分排序</span>
            </div>
            <p v-if="!discoveryCandidates.length" class="quiet">当前没有自动发现候选。超级管理员可以点击“立即发现”。</p>
            <article v-for="candidate in discoveryCandidates" :key="candidate.id" class="candidate-card">
              <div class="candidate-head">
                <div>
                  <strong>{{ candidate.title }}</strong>
                  <span>{{ candidate.repository }} · {{ candidate.dataFormat }} · {{ candidate.method }}</span>
                </div>
                <em :class="scoreClass(candidate.score)">{{ candidate.score }}</em>
              </div>
              <p>{{ candidate.recommendation }}</p>
              <div class="candidate-meta">
                <span :class="['relevance-tag', relevanceClass(candidate.relevance)]">相关度 {{ candidate.relevance }}</span>
                <span v-if="candidate.validationStatus" :class="['validation-tag', validationClass(candidate.validationStatus)]">{{ validationLabel(candidate.validationStatus) }}</span>
                <span>{{ candidate.detectedFields }}</span>
                <span>{{ candidate.dataScale }}</span>
                <span>{{ discoveryStatusLabel(candidate.status) }}</span>
              </div>
              <div class="candidate-links">
                <a v-if="candidate.paperUrl" :href="candidate.paperUrl" target="_blank" rel="noopener noreferrer">论文 / DOI</a>
                <a v-if="candidate.dataUrl" :href="candidate.dataUrl" target="_blank" rel="noopener noreferrer">数据入口</a>
              </div>
              <div class="parser-plan">{{ candidate.parserPlan }}</div>
              <div class="validation-panel">
                <div class="validation-head">
                  <strong :class="validationClass(candidate.validationStatus)">自动校对 · {{ validationLabel(candidate.validationStatus) }}</strong>
                  <span v-if="candidate.validatedAt" class="vts">{{ formatDate(candidate.validatedAt) }}</span>
                  <button v-if="isAdminOrSuperAdmin" type="button" :disabled="working" @click="revalidateCandidate(candidate)">重新校对</button>
                </div>
                <ul v-if="candidate.validationChecks && candidate.validationChecks.length">
                  <li v-for="chk in candidate.validationChecks" :key="chk.key" :class="validationClass(chk.status)">
                    <i>{{ chk.status }}</i>
                    <span>{{ chk.label }}</span>
                    <em>{{ chk.detail }}</em>
                  </li>
                </ul>
                <p v-else class="quiet">尚未校对（可在配置中开启自动校对，或点击「重新校对」）。</p>
              </div>
              <template v-if="isSuperAdmin && !['APPROVED', 'REJECTED'].includes(candidate.status)">
                <textarea v-model="discoveryNotes[candidate.id]" rows="2" placeholder="候选审核意见，可记录许可证、重复性或适配器要求"></textarea>
                <div class="review-actions">
                  <button type="button" :disabled="working" @click="reviewCandidate(candidate, 'REVIEW')">标记复核</button>
                  <button type="button" :disabled="working" @click="promoteCandidate(candidate)">转入接入队列</button>
                  <button class="reject" type="button" :disabled="working" @click="reviewCandidate(candidate, 'REJECTED')">拒绝</button>
                </div>
              </template>
            </article>
          </section>

          <aside>
            <div class="section-title compact">
              <h3>发现运行记录</h3>
              <span>最近 20 次</span>
            </div>
            <p v-if="!discoveryRuns.length" class="quiet">暂无运行记录。</p>
            <article v-for="run in discoveryRuns" :key="run.runId" class="run-card">
              <strong :class="runStatusClass(run.status)">{{ run.status }}</strong>
              <span>{{ formatDate(run.startedAt) }}</span>
              <p>发现 {{ run.discovered }} · 新增 {{ run.inserted }} · 更新 {{ run.updated }}</p>
              <p class="auto-line">自动转入 {{ run.autoPromoted }} · 自动归档 {{ run.autoArchived }}</p>
              <div v-if="run.sourceStats && run.sourceStats.length" class="source-chips">
                <em
                  v-for="s in run.sourceStats"
                  :key="s.source"
                  :class="sourceStatClass(s.status)"
                  :title="s.detail"
                >{{ s.source }} · {{ s.found }}</em>
              </div>
              <small>{{ run.message }}</small>
            </article>
          </aside>
        </div>
        <div v-else-if="isAuthenticated" class="login-required compact-login">
          <strong>当前账号无权查看自动发现候选</strong>
          <p>注册用户可以提交推荐数据集并查看自己的投稿进度；候选数据集、运行记录和自动评分仅管理员/超级管理员可见。</p>
        </div>
        <div v-else class="login-required compact-login">
          <strong>登录后可提交推荐数据集</strong>
          <p>游客可查看连接器范围；注册用户可提交数据集线索；候选发现与审核明细仅管理员/超级管理员可见。</p>
          <button type="button" @click="openLogin">登录或注册</button>
        </div>
      </section>

      <section class="pipeline">
        <div class="section-title">
          <h2>数据更新管线</h2>
          <span>受控发布框架</span>
        </div>
        <div class="pipeline-flow">
          <div v-for="(step, index) in pipelineSteps" :key="step.key" class="pipeline-step">
            <i>{{ index + 1 }}</i>
            <strong>{{ step.label }}</strong>
            <p>{{ step.note }}</p>
          </div>
        </div>
        <p class="pipeline-note">
          公开 API 或下载链接可成为自动同步源；不同 HDF5、CIF、JSON 或 LMDB 数据仍需对应解析适配器和质量校验，避免未经确认的字段直接发布。
        </p>
      </section>

      <section class="source-section">
        <div class="section-title">
          <h2>候选公开来源</h2>
          <span>人工维护清单</span>
        </div>
        <div v-if="loading" class="state">正在读取来源目录...</div>
        <div v-else class="source-grid">
          <article v-for="source in sources" :key="source.key" class="source-card">
            <div class="source-head">
              <div>
                <strong>{{ source.name }}</strong>
                <span>{{ source.provider }}</span>
              </div>
              <em>{{ source.accessType }}</em>
            </div>
            <p>{{ source.coverage }}</p>
            <a :href="source.url" target="_blank" rel="noopener noreferrer">访问公开来源</a>
            <div class="adapter">{{ source.adapterStatus }}</div>
          </article>
        </div>
      </section>

      <div class="workspace">
        <section class="submission-panel">
          <div class="section-title">
            <h2>推荐数据集</h2>
            <span>注册用户可提交</span>
          </div>

          <div v-if="!isAuthenticated" class="login-required">
            <strong>登录后可提交数据集来源</strong>
            <p>游客可以浏览接入流程和公开来源，注册用户只需提供说明和至少一个网页链接即可提交。</p>
            <button type="button" @click="openLogin">登录或注册</button>
          </div>

          <form v-else class="proposal-form" @submit.prevent="submitProposal">
            <label>
              <span>数据集名称（选填）</span>
              <input v-model="form.datasetName" maxlength="180" placeholder="例如：新材料计算数据集">
            </label>
            <label>
              <span>数据类型（选填）</span>
              <select v-model="form.dataType">
                <option value="">暂不确定</option>
                <option>小分子构象</option>
                <option>聚合物</option>
                <option>晶体材料</option>
                <option>二维材料</option>
                <option>MOF</option>
                <option>反应路径</option>
                <option>其他</option>
              </select>
            </label>
            <label class="wide">
              <span>数据集说明（必填）</span>
              <textarea v-model="form.description" required maxlength="2000" rows="3" placeholder="研究对象、计算方法、规模与用途"></textarea>
            </label>
            <label>
              <span>论文链接 / DOI（二选一）</span>
              <input v-model="form.paperUrl" type="url" placeholder="https://doi.org/...">
            </label>
            <label>
              <span>数据下载或入口链接（二选一）</span>
              <input v-model="form.dataUrl" type="url" placeholder="https://...">
            </label>
            <label>
              <span>数据格式（选填）</span>
              <input v-model="form.dataFormat" maxlength="80" placeholder="HDF5 / JSON / CIF / LMDB">
            </label>
            <label>
              <span>许可证（选填）</span>
              <input v-model="form.license" maxlength="180" placeholder="如 CC BY 4.0；未知可留空">
            </label>
            <label class="wide">
              <span>可提供字段（选填）</span>
              <textarea v-model="form.providedFields" maxlength="1000" rows="2" placeholder="结构、能量、力、band gap、泛函、基组等"></textarea>
            </label>
            <button class="submit" type="submit" :disabled="working">提交审核</button>
          </form>
        </section>

        <section class="queue-panel">
          <div class="section-title">
            <h2>{{ submissionHeading }}</h2>
            <span v-if="isAuthenticated">{{ authUser.displayName }}</span>
          </div>
          <div v-if="isAuthenticated" class="queue-stats">
            <article v-for="item in intakeQueueStats" :key="item.label">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </article>
          </div>
          <div v-if="isSuperAdmin" class="admin-guide">
            <article v-for="item in adminPipelineGuide" :key="item.title">
              <strong>{{ item.title }}</strong>
              <p>{{ item.text }}</p>
            </article>
          </div>
          <p v-if="!isAuthenticated" class="quiet">登录后可查看自己的投稿进度。</p>
          <p v-else-if="!submissions.length" class="quiet">当前没有投稿记录。</p>
          <article v-for="submission in submissions" :key="submission.id" class="task">
            <div class="task-head">
              <div>
                <strong>{{ submission.datasetName }}</strong>
                <span>{{ submission.dataType }} · {{ submission.dataFormat }} · {{ submission.submittedBy }}</span>
              </div>
              <em :class="submission.status.toLowerCase()">{{ statusLabel(submission.status) }}</em>
            </div>
            <p>{{ submission.description }}</p>
            <div class="task-links">
              <a v-if="submission.paperUrl" :href="submission.paperUrl" target="_blank" rel="noopener noreferrer">论文</a>
              <a v-if="submission.dataUrl" :href="submission.dataUrl" target="_blank" rel="noopener noreferrer">数据来源</a>
              <span>{{ formatDate(submission.updatedAt) }}</span>
            </div>
            <div class="task-source-meta">
              <div>
                <span>DOI</span>
                <strong>{{ inferDoi(submission) || '未识别 / 未提供' }}</strong>
              </div>
              <div>
                <span>论文链接</span>
                <a v-if="submission.paperUrl" :href="submission.paperUrl" target="_blank" rel="noopener noreferrer">{{ shortUrl(submission.paperUrl) }}</a>
                <strong v-else>未提供</strong>
              </div>
              <div>
                <span>数据下载/入口链接</span>
                <a v-if="submission.dataUrl" :href="submission.dataUrl" target="_blank" rel="noopener noreferrer">{{ shortUrl(submission.dataUrl) }}</a>
                <strong v-else>未提供</strong>
              </div>
              <div>
                <span>许可证</span>
                <strong>{{ submission.license || '未提供' }}</strong>
              </div>
              <div>
                <span>声明格式</span>
                <strong>{{ submission.dataFormat || '未填写' }}</strong>
              </div>
              <div>
                <span>提供字段</span>
                <strong>{{ submission.providedFields || '未填写' }}</strong>
              </div>
            </div>
            <div class="stage">
              <strong>{{ stageLabel(submission.pipelineStage) }}</strong>
              <p>{{ submission.pipelineMessage }}</p>
            </div>
            <div class="task-progress">
              <span
                v-for="(step, index) in pipelineSteps"
                :key="step.key"
                :class="stepState(submission, index)"
              >
                {{ step.label }}
              </span>
            </div>
            <div class="next-action">
              <strong>{{ isSuperAdmin ? '管理员下一步' : '当前处理说明' }}</strong>
              <p>{{ adminNextAction(submission) }}</p>
            </div>
            <template v-if="isSuperAdmin && submission.status === 'SUBMITTED'">
              <textarea v-model="reviewNotes[submission.id]" rows="2" placeholder="审核意见（可选）"></textarea>
              <div class="review-actions">
                <button type="button" :disabled="working" @click="review(submission, 'APPROVED')">批准来源</button>
                <button class="reject" type="button" :disabled="working" @click="review(submission, 'REJECTED')">退回</button>
              </div>
            </template>
            <button
              v-if="isSuperAdmin && submission.status === 'APPROVED' && submission.pipelineStage === 'SOURCE_APPROVED'"
              class="prepare"
              type="button"
              :disabled="working"
              @click="prepare(submission)"
            >进入格式适配阶段</button>
            <button
              v-if="isSuperAdmin && submission.status === 'APPROVED'"
              class="adapt"
              type="button"
              :disabled="working"
              @click="adaptSubmission(submission)"
            >{{ adaptButtonText(submission) }}</button>
            <button
              v-if="isSuperAdmin && submission.status === 'APPROVED' && submission.dataUrl"
              class="save-source"
              type="button"
              :disabled="working"
              @click="saveSubmissionSource(submission)"
            >{{ activeSaveId === submission.id ? '保存中...' : '保存源文件到本地' }}</button>
            <div v-if="adaptProgress[submission.id]" class="adapt-progress" :class="adaptProgress[submission.id].status">
              <div class="adapt-progress-head">
                <strong>{{ adaptProgress[submission.id].label }}</strong>
                <span>{{ adaptProgress[submission.id].percent }}%</span>
              </div>
              <div class="adapt-progress-track">
                <i :style="{ width: adaptProgress[submission.id].percent + '%' }"></i>
              </div>
              <p>{{ adaptProgress[submission.id].detail }}</p>
              <div class="adapt-steps">
                <span
                  v-for="step in adaptSteps"
                  :key="step.key"
                  :class="adaptStepState(adaptProgress[submission.id], step)"
                >{{ step.label }}</span>
              </div>
            </div>
            <div v-if="parsedProfile(submission)" class="parse-profile">
              <strong>自动解析预检结果</strong>
              <div class="pp-grid">
                <template v-if="parsedProfile(submission).fileName">
                  <span>文件名</span><em>{{ parsedProfile(submission).fileName }}</em>
                </template>
                <span>格式</span><em>{{ parsedProfile(submission).format || '-' }}</em>
                <span>状态</span><em>{{ parsedProfile(submission).status || '-' }}</em>
                <template v-if="parsedProfile(submission).containerFormat">
                  <span>外层容器</span><em>{{ parsedProfile(submission).containerFormat }}</em>
                </template>
                <template v-if="parsedProfile(submission).selectedEntry || parsedProfile(submission).innerFile">
                  <span>内部文件</span><em>{{ parsedProfile(submission).selectedEntry || parsedProfile(submission).innerFile }}</em>
                </template>
                <template v-if="parsedProfile(submission).innerFormat">
                  <span>内部格式</span><em>{{ parsedProfile(submission).innerFormat }}</em>
                </template>
                <template v-if="parsedProfile(submission).sizeBytes">
                  <span>远程大小</span><em>{{ formatBytes(parsedProfile(submission).sizeBytes) }}</em>
                </template>
                <template v-if="parsedProfile(submission).sampleBytes">
                  <span>样本大小</span><em>{{ formatBytes(parsedProfile(submission).sampleBytes) }}</em>
                </template>
                <template v-if="parsedProfile(submission).sampled !== undefined">
                  <span>抽样状态</span><em>{{ parsedProfile(submission).sampled ? '样本截断 / Range 抽样' : '未截断' }}</em>
                </template>
                <template v-if="parsedProfile(submission).recordEstimate">
                  <span>记录</span><em>{{ parsedProfile(submission).recordEstimate }}</em>
                </template>
                <template v-if="parsedProfile(submission).detectedFields">
                  <span>识别字段</span><em>{{ parsedProfile(submission).detectedFields }}</em>
                </template>
                <template v-if="parsedProfile(submission).elements && parsedProfile(submission).elements.length">
                  <span>元素</span><em>{{ parsedProfile(submission).elements.join(' ') }}</em>
                </template>
              </div>
              <p v-if="parsedProfile(submission).summary" class="pp-summary">
                {{ parsedProfile(submission).summary }}
              </p>
              <div class="profile-scale">
                <strong>数据规模</strong>
                <span>远程大小：{{ formatBytes(parsedProfile(submission).sizeBytes) }}</span>
                <span>样本大小：{{ formatBytes(parsedProfile(submission).sampleBytes) }}</span>
                <span>记录估计：{{ parsedProfile(submission).recordEstimate || '未识别' }}</span>
                <span>本地保存：{{ parsedProfile(submission).localSaved ? formatBytes(parsedProfile(submission).localSizeBytes) : '未保存' }}</span>
              </div>
              <div v-if="parsedProfile(submission).localSaved" class="local-save">
                <span>本地路径</span>
                <strong>{{ parsedProfile(submission).localPath }}</strong>
                <span>SHA-256</span>
                <strong>{{ parsedProfile(submission).localSha256 }}</strong>
              </div>
              <p v-if="parsedProfile(submission).localSaveError" class="pp-warn">
                本地保存失败：{{ parsedProfile(submission).localSaveError }}
              </p>
              <p v-if="parsedProfile(submission).archiveEntries && parsedProfile(submission).archiveEntries.length" class="pp-fields">
                归档文件：{{ parsedProfile(submission).archiveEntries.join('、') }}
              </p>
              <p v-if="parsedProfile(submission).fields && parsedProfile(submission).fields.length" class="pp-fields">
                字段/列：{{ parsedProfile(submission).fields.join('、') }}
              </p>
              <p v-if="parsedProfile(submission).warning" class="pp-warn">{{ parsedProfile(submission).warning }}</p>
            </div>
            <button
              v-if="isSuperAdmin && submission.status === 'APPROVED' && parsedProfile(submission) && !ingestState[submission.id]"
              class="adapt ingest-start"
              type="button"
              :disabled="working"
              @click="loadIngest(submission)"
            >准备入库映射</button>
            <div v-if="ingestState[submission.id]" class="ingest-panel">
              <strong>半自动入库 · 写入展示库</strong>
              <p class="ingest-note">{{ ingestState[submission.id].suggestion.note }}</p>
              <p
                v-if="ingestState[submission.id].suggestion.sourceFields && ingestState[submission.id].suggestion.sourceFields.length"
                class="ingest-fields"
              >来源字段：{{ ingestState[submission.id].suggestion.sourceFields.join('、') }}</p>
              <label class="ingest-label">
                <span>数据集名称</span>
                <input v-model="ingestState[submission.id].datasetName" maxlength="120" />
              </label>
              <label
                v-if="!['XYZ','EXTXYZ','CIF','POSCAR','HDF5'].includes(ingestState[submission.id].suggestion.format)"
                class="ingest-label"
              >
                <span>字段映射（展示库列 → 来源字段，JSON）</span>
                <textarea v-model="ingestState[submission.id].mappingText" rows="6"></textarea>
              </label>
              <p
                v-else
                class="ingest-fields"
              >结构格式（{{ ingestState[submission.id].suggestion.format }}）将自动解析元素/坐标/化学式，无需字段映射。</p>
              <label class="ingest-label">
                <span>最多入库条数</span>
                <input v-model="ingestState[submission.id].limit" type="number" min="1" max="1000" />
              </label>
              <div class="review-actions">
                <button
                  type="button"
                  :disabled="working || !ingestState[submission.id].suggestion.supported"
                  @click="doIngest(submission)"
                >确认并写入展示库</button>
                <button class="reject" type="button" :disabled="working" @click="ingestState[submission.id] = null">取消</button>
              </div>
              <p v-if="!ingestState[submission.id].suggestion.supported" class="pp-warn">当前格式不支持直接入库（支持 CSV/TSV/JSON/JSONL 表格与 XYZ/EXTXYZ/CIF/POSCAR/HDF5 结构）。</p>
            </div>
            <div v-if="isSuperAdmin && submission.pipelineStage === 'PUBLISHED'" class="withdraw-row">
              <span>已入库展示库数据集 intake_{{ submission.id }}（可在数据中心查看 / 重新入库覆盖）。</span>
              <button class="reject" type="button" :disabled="working" @click="withdrawSubmission(submission)">撤回入库</button>
            </div>
          </article>
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
select,
textarea {
  font: inherit;
}

.shell {
  width: min(1540px, calc(100% - 48px));
  margin: 0 auto;
  padding: 34px 0 52px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 30px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

h1 {
  margin: 0 0 10px;
  color: var(--vs-text);
  font-size: 34px;
}

.page-head p:not(.eyebrow) {
  max-width: 850px;
  margin: 0;
  color: var(--vs-text-secondary);
  line-height: 1.7;
}

.head-stat {
  width: 166px;
  padding: 18px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
}

.head-stat strong {
  display: block;
  color: var(--vs-primary);
  font-size: 30px;
}

.head-stat span {
  color: var(--vs-text-secondary);
  font-size: 13px;
}

.pipeline,
.source-section,
.discovery-panel,
.submission-panel,
.queue-panel {
  padding: 22px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
}

.pipeline,
.discovery-panel,
.source-section {
  margin-bottom: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  margin-bottom: 18px;
}

.section-title h2 {
  margin: 0;
  font-size: 19px;
}

.section-title h3 {
  margin: 0;
  color: var(--vs-text);
  font-size: 16px;
}

.section-title p {
  margin: 6px 0 0;
  max-width: 920px;
  color: var(--vs-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.section-title span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 800;
}

.section-title.compact {
  margin-bottom: 12px;
}

.run-discovery {
  flex: 0 0 auto;
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font-weight: 900;
}

.discovery-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.discovery-stats article {
  padding: 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.discovery-stats strong,
.discovery-stats span {
  display: block;
}

.discovery-stats strong {
  color: var(--vs-primary);
  font-size: 26px;
  line-height: 1;
}

.discovery-stats span {
  margin-top: 6px;
  color: var(--vs-text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.connector-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.connector-grid article,
.candidate-card,
.run-card {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.connector-grid article {
  padding: 13px;
}

.connector-grid div,
.candidate-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.connector-grid strong,
.connector-grid span,
.connector-grid p,
.connector-grid small {
  display: block;
}

.connector-grid strong {
  color: var(--vs-text);
  font-size: 13px;
}

.connector-grid span {
  height: fit-content;
  padding: 4px 7px;
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
  color: var(--vs-primary);
  font-size: 11px;
  font-weight: 900;
}

.connector-grid p {
  min-height: 42px;
  margin: 10px 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.connector-grid small {
  color: var(--vs-text-tertiary);
  font-size: 11px;
}

.discovery-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 14px;
  align-items: start;
}

.candidate-card {
  padding: 14px;
  margin-top: 10px;
}

.candidate-head strong,
.candidate-head span {
  display: block;
}

.candidate-head strong {
  color: var(--vs-text);
  font-size: 15px;
  line-height: 1.35;
}

.candidate-head span {
  margin-top: 5px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.candidate-head em {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 44px;
  height: 36px;
  border-radius: var(--vs-radius-sm);
  font-style: normal;
  font-weight: 900;
}

.candidate-head em.high {
  background: #e8f7ee;
  color: #168151;
}

.candidate-head em.mid {
  background: #fff7df;
  color: #9a6a16;
}

.candidate-head em.low {
  background: #fff0ef;
  color: #d25955;
}

.candidate-card > p {
  color: var(--vs-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.candidate-meta,
.candidate-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0;
}

.candidate-meta span {
  padding: 5px 8px;
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 11px;
  font-weight: 800;
}

.candidate-links a {
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-decoration: none;
}

.parser-plan {
  padding: 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.run-card {
  padding: 12px;
  margin-top: 10px;
}

.run-card strong,
.run-card span,
.run-card p,
.run-card small {
  display: block;
}

.run-card strong {
  color: var(--vs-primary);
  font-size: 13px;
}

.run-card span,
.run-card small {
  color: var(--vs-text-tertiary);
  font-size: 11px;
}

.run-card p {
  margin: 8px 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.run-card p.auto-line {
  margin-top: 0;
  color: var(--vs-primary);
  font-weight: 800;
}

.run-card strong.ok {
  color: #168151;
}

.run-card strong.warn {
  color: #9a6a16;
}

.run-card strong.bad {
  color: #d25955;
}

.source-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 6px 0 8px;
}

.source-chips em {
  padding: 3px 7px;
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  border: 1px solid var(--vs-border);
  color: var(--vs-text-secondary);
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: help;
}

.source-chips em.ok {
  background: #e8f7ee;
  color: #168151;
  border-color: #bfe6cf;
}

.source-chips em.bad {
  background: #fff0ef;
  color: #d25955;
  border-color: #f2c9c6;
}

.source-chips em.muted {
  color: var(--vs-text-tertiary);
}

.candidate-meta span.relevance-tag {
  font-weight: 800;
}

.candidate-meta span.relevance-tag.high {
  background: #e8f7ee;
  color: #168151;
}

.candidate-meta span.relevance-tag.mid {
  background: #fff7df;
  color: #9a6a16;
}

.candidate-meta span.relevance-tag.low {
  background: #fff0ef;
  color: #d25955;
}

.candidate-meta span.validation-tag {
  font-weight: 800;
}

.candidate-meta span.validation-tag.ok {
  background: #e8f7ee;
  color: #168151;
}

.candidate-meta span.validation-tag.warn {
  background: #fff7df;
  color: #9a6a16;
}

.candidate-meta span.validation-tag.bad {
  background: #fff0ef;
  color: #d25955;
}

.validation-panel {
  margin: 10px 0;
  padding: 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.validation-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.validation-head strong {
  font-size: 12px;
  font-weight: 900;
}

.validation-head strong.ok {
  color: #168151;
}

.validation-head strong.warn {
  color: #9a6a16;
}

.validation-head strong.bad {
  color: #d25955;
}

.validation-head .vts {
  color: var(--vs-text-tertiary);
  font-size: 11px;
}

.validation-head button {
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 11px;
  cursor: pointer;
}

.validation-head button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.validation-panel ul {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: grid;
  gap: 4px;
}

.validation-panel li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 11px;
  color: var(--vs-text-secondary);
}

.validation-panel li i {
  flex: 0 0 auto;
  width: 38px;
  text-align: center;
  padding: 1px 0;
  border-radius: 4px;
  font-style: normal;
  font-weight: 800;
  font-size: 10px;
}

.validation-panel li.ok i {
  background: #e8f7ee;
  color: #168151;
}

.validation-panel li.warn i {
  background: #fff7df;
  color: #9a6a16;
}

.validation-panel li.bad i {
  background: #fff0ef;
  color: #d25955;
}

.validation-panel li span {
  flex: 0 0 auto;
  font-weight: 700;
}

.validation-panel li em {
  font-style: normal;
  color: var(--vs-text-tertiary);
}

.compact-login {
  margin-top: 12px;
}

.pipeline-flow {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.pipeline-step {
  min-height: 112px;
  padding: 13px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.pipeline-step i {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  margin-bottom: 10px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--vs-primary) 14%, var(--vs-card));
  color: var(--vs-primary);
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
}

.pipeline-step strong {
  display: block;
  color: var(--vs-text);
  font-size: 14px;
}

.pipeline-step p,
.pipeline-note {
  margin: 7px 0 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.pipeline-note {
  margin-top: 16px;
}

.source-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.source-card,
.task {
  padding: 15px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.source-head,
.task-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.source-head strong,
.task-head strong {
  display: block;
  color: var(--vs-text);
}

.source-head span,
.task-head span {
  display: block;
  margin-top: 4px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.source-head em,
.task-head em {
  height: fit-content;
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-primary) 11%, var(--vs-card));
  color: var(--vs-primary);
  padding: 5px 7px;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.source-card p,
.task > p {
  min-height: 42px;
  color: var(--vs-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.source-card a,
.task-links a {
  color: var(--vs-primary);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.adapter {
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px solid var(--vs-border);
  color: var(--vs-text-secondary);
  font-size: 12px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(410px, .96fr) minmax(410px, 1.04fr);
  gap: 16px;
}

.proposal-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px;
}

.proposal-form label {
  display: grid;
  gap: 7px;
  color: var(--vs-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.proposal-form label.wide {
  grid-column: 1 / -1;
}

input,
select,
textarea {
  width: 100%;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-elevated);
  color: var(--vs-text);
  padding: 10px 12px;
}

input,
select {
  height: 42px;
}

textarea {
  resize: vertical;
}

.submit,
.login-required button,
.review-actions button,
.prepare {
  height: 42px;
  padding: 0 15px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.submit {
  grid-column: 1 / -1;
}

.login-required {
  padding: 28px;
  border: 1px dashed var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
  text-align: center;
}

.login-required strong {
  color: var(--vs-text);
}

.login-required p,
.quiet {
  color: var(--vs-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.task {
  margin-top: 12px;
}

.queue-stats,
.admin-guide {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}

.queue-stats {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.queue-stats article,
.admin-guide article,
.next-action {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
}

.queue-stats article {
  padding: 10px 12px;
}

.queue-stats strong,
.queue-stats span,
.admin-guide strong,
.admin-guide p,
.next-action strong,
.next-action p {
  display: block;
}

.queue-stats strong {
  color: var(--vs-primary);
  font-size: 22px;
  line-height: 1;
}

.queue-stats span {
  margin-top: 6px;
  color: var(--vs-text-secondary);
  font-size: 11px;
  font-weight: 800;
}

.admin-guide {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.admin-guide article {
  padding: 11px;
}

.admin-guide strong,
.next-action strong {
  color: var(--vs-text);
  font-size: 12px;
}

.admin-guide p,
.next-action p {
  margin: 6px 0 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.task-head em.approved {
  background: #e8f7ee;
  color: #168151;
}

.task-head em.rejected {
  background: #fff0ef;
  color: #d25955;
}

.task-links {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 10px 0;
}

.task-links span {
  margin-left: auto;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.task-source-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 10px 0;
}

.task-source-meta div {
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-card) 82%, var(--vs-cyan-100));
}

.task-source-meta span,
.task-source-meta strong,
.task-source-meta a {
  display: block;
}

.task-source-meta span {
  color: var(--vs-text-tertiary);
  font-size: 10px;
  font-weight: 900;
}

.task-source-meta strong,
.task-source-meta a {
  margin-top: 4px;
  overflow-wrap: anywhere;
  color: var(--vs-text);
  font-size: 11px;
  font-weight: 800;
  line-height: 1.45;
}

.task-source-meta a {
  color: var(--vs-primary);
}

.stage {
  padding: 11px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
}

.stage strong {
  color: var(--vs-primary);
  font-size: 12px;
}

.stage p {
  margin: 5px 0 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.task-progress {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
  margin-top: 10px;
}

.task-progress span {
  min-height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text-tertiary);
  font-size: 10.5px;
  font-weight: 850;
  text-align: center;
}

.task-progress span.done {
  border-color: color-mix(in srgb, #43a867 34%, var(--vs-border));
  background: color-mix(in srgb, #43a867 10%, var(--vs-card));
  color: #24815b;
}

.task-progress span.active {
  border-color: color-mix(in srgb, var(--vs-primary) 45%, var(--vs-border));
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
  color: var(--vs-primary);
}

.task-progress span.blocked {
  border-color: #f1caca;
  background: #fff3f3;
  color: #cc5656;
}

.next-action {
  margin-top: 10px;
  padding: 11px;
}

.task textarea {
  margin-top: 12px;
}

.review-actions {
  display: flex;
  gap: 9px;
  margin-top: 9px;
}

.review-actions .reject {
  border-color: var(--vs-border-strong);
  background: var(--vs-card);
  color: #d25955;
}

.prepare {
  margin-top: 12px;
}

.feedback,
.state {
  padding: 13px 15px;
  border-radius: var(--vs-radius-md);
  font-size: 13px;
}

.feedback.error {
  border: 1px solid #f1caca;
  background: #fff3f3;
  color: #cc5656;
}

.feedback.success {
  border: 1px solid #bfe6d0;
  background: #edf9f2;
  color: #168151;
}

.state {
  color: var(--vs-text-secondary);
}

.config-panel {
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.config-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.config-head strong {
  color: var(--vs-text);
  font-size: 14px;
}

.config-head span {
  color: var(--vs-text-tertiary);
  font-size: 11px;
  font-weight: 800;
}

.config-head .cfg-edit {
  margin-left: auto;
  padding: 5px 12px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.config-form {
  display: grid;
  gap: 12px;
}

.config-form .cfg-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
}

.config-form .cfg-label {
  color: var(--vs-text-tertiary);
  font-size: 11px;
  font-weight: 800;
}

.config-form label.chk {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--vs-text-secondary);
  font-weight: 700;
}

.config-form label.num {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--vs-text-secondary);
}

.config-form label.num input {
  width: 78px;
  padding: 4px 6px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text);
}

.config-form .cfg-block {
  display: grid;
  gap: 6px;
  font-size: 12px;
  color: var(--vs-text-secondary);
  font-weight: 700;
}

.config-form .cfg-block textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-text);
  font-family: var(--vs-font-mono, monospace);
  font-size: 12px;
  resize: vertical;
}

.config-form .cfg-actions {
  display: flex;
  gap: 10px;
}

.config-form .cfg-actions button {
  padding: 7px 16px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.config-form .cfg-actions button.ghost {
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  border-color: var(--vs-border);
}

.config-form .cfg-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.config-grid article {
  padding: 10px 12px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
}

.config-grid span {
  display: block;
  color: var(--vs-text-tertiary);
  font-size: 11px;
  font-weight: 800;
}

.config-grid strong {
  display: block;
  margin: 5px 0 3px;
  color: var(--vs-primary);
  font-size: 15px;
}

.config-grid small {
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: 1.4;
}

.config-queries {
  margin-top: 10px;
}

.config-queries > span {
  color: var(--vs-text-tertiary);
  font-size: 11px;
  font-weight: 800;
}

.config-queries div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.config-queries em {
  font-style: normal;
  padding: 4px 8px;
  border: 1px solid var(--vs-border);
  border-radius: 999px;
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 11px;
}

.adapt {
  margin-top: 10px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-primary) 12%, var(--vs-card));
  color: var(--vs-primary);
  cursor: pointer;
  font-weight: 800;
}

.parse-profile {
  margin-top: 10px;
  padding: 11px;
  border: 1px solid color-mix(in srgb, var(--vs-cyan-500) 30%, var(--vs-border));
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-cyan-100) 30%, var(--vs-card));
}

.adapt-progress {
  margin-top: 10px;
  padding: 11px;
  border: 1px solid color-mix(in srgb, var(--vs-primary) 26%, var(--vs-border));
  border-radius: var(--vs-radius-sm);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--vs-primary) 7%, transparent), transparent 54%),
    color-mix(in srgb, var(--vs-card) 92%, var(--vs-cyan-100));
}

.adapt-progress.done {
  border-color: color-mix(in srgb, #16a36b 34%, var(--vs-border));
}

.adapt-progress.failed {
  border-color: color-mix(in srgb, #d85a5a 38%, var(--vs-border));
}

.adapt-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.adapt-progress-head strong,
.adapt-progress-head span {
  color: var(--vs-text);
  font-size: 12px;
  font-weight: 900;
}

.adapt-progress-track {
  height: 7px;
  margin-top: 9px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
}

.adapt-progress-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--vs-primary), var(--vs-cyan-500));
  transition: width .28s ease;
}

.adapt-progress.failed .adapt-progress-track i {
  background: linear-gradient(90deg, #d85a5a, #ef9a9a);
}

.adapt-progress p {
  margin: 8px 0 0;
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: 1.5;
}

.adapt-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.adapt-steps span {
  padding: 4px 7px;
  border: 1px solid var(--vs-border);
  border-radius: 999px;
  color: var(--vs-text-tertiary);
  font-size: 10px;
  font-weight: 900;
}

.adapt-steps span.done {
  border-color: color-mix(in srgb, var(--vs-cyan-500) 42%, var(--vs-border));
  background: color-mix(in srgb, var(--vs-cyan-100) 48%, var(--vs-card));
  color: var(--vs-primary);
}

.adapt-steps span.active {
  border-color: var(--vs-primary);
  color: var(--vs-primary);
}

.adapt-steps span.blocked {
  border-color: color-mix(in srgb, #d85a5a 40%, var(--vs-border));
  color: #c34b4b;
}

.parse-profile > strong {
  color: var(--vs-text);
  font-size: 12px;
}

.pp-grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 12px;
  margin-top: 8px;
}

.pp-grid span {
  color: var(--vs-text-tertiary);
  font-size: 11px;
  font-weight: 800;
}

.pp-grid em {
  font-style: normal;
  color: var(--vs-text);
  font-size: 12px;
}

.pp-fields {
  margin: 8px 0 0;
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: 1.5;
}

.pp-summary {
  margin: 9px 0 0;
  padding: 8px 9px;
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-card) 70%, var(--vs-cyan-100));
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: 1.55;
}

.pp-warn {
  margin: 6px 0 0;
  color: #cc5656;
  font-size: 11px;
}

.withdraw-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--vs-border);
}

.withdraw-row span {
  color: var(--vs-text-tertiary);
  font-size: 11px;
}

.ingest-start {
  border-color: var(--vs-cyan-600);
  background: color-mix(in srgb, var(--vs-cyan-500) 14%, var(--vs-card));
  color: var(--vs-cyan-700);
}

.save-source {
  margin-top: 12px;
  margin-left: 8px;
  border-color: color-mix(in srgb, #16a36b 45%, var(--vs-border));
  background: color-mix(in srgb, #16a36b 10%, var(--vs-card));
  color: #168151;
}

.profile-scale {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 7px;
  margin-top: 9px;
}

.profile-scale strong {
  grid-column: 1 / -1;
  color: var(--vs-text);
  font-size: 11px;
}

.profile-scale span {
  padding: 7px 8px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-card) 86%, var(--vs-blue-100));
  color: var(--vs-text-secondary);
  font-size: 11px;
  font-weight: 800;
}

.local-save {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 6px 10px;
  margin-top: 9px;
  padding: 9px;
  border: 1px dashed color-mix(in srgb, #16a36b 34%, var(--vs-border));
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, #16a36b 7%, var(--vs-card));
}

.local-save span {
  color: var(--vs-text-tertiary);
  font-size: 10px;
  font-weight: 900;
}

.local-save strong {
  overflow-wrap: anywhere;
  color: var(--vs-text);
  font-size: 11px;
  font-weight: 800;
}

.ingest-panel {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--vs-primary) 30%, var(--vs-border));
  border-radius: var(--vs-radius-sm);
  background: color-mix(in srgb, var(--vs-blue-100) 30%, var(--vs-card));
}

.ingest-panel > strong {
  color: var(--vs-text);
  font-size: 12px;
}

.ingest-note {
  margin: 6px 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.ingest-fields {
  margin: 6px 0;
  color: var(--vs-text-tertiary);
  font-size: 11px;
  line-height: 1.5;
}

.ingest-label {
  display: grid;
  gap: 5px;
  margin-top: 8px;
  color: var(--vs-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.ingest-label textarea {
  font-family: var(--vs-font-mono);
  font-size: 12px;
}

@media (max-width: 1120px) {
  .source-grid,
  .connector-grid,
  .discovery-stats,
  .config-grid,
  .queue-stats,
  .admin-guide,
  .task-source-meta,
  .profile-scale,
  .pipeline-flow {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .discovery-workspace,
  .workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page-head {
    align-items: stretch;
    flex-direction: column;
  }

  .source-grid,
  .connector-grid,
  .discovery-stats,
  .config-grid,
  .queue-stats,
  .admin-guide,
  .task-source-meta,
  .profile-scale,
  .task-progress,
  .pipeline-flow,
  .proposal-form {
    grid-template-columns: 1fr;
  }

  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
