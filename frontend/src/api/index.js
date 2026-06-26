import { authHeaders, clearSession, setSession } from '../auth/session'

const BASE_URL = '/api'

async function request(path, opts = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...authHeaders(), ...(opts.headers || {}) },
  })

  if (!res.ok) {
    let message = `API ${path} failed: ${res.status}`
    try {
      const body = await res.json()
      if (body?.message) message = body.message
    } catch {
      // Keep the HTTP status fallback when the response is not JSON.
    }
    throw new Error(message)
  }

  return res.json()
}

export function fetchDatasets() {
  return request('/datasets')
}

export function fetchDatasetCatalog() {
  return request('/datasets/catalog')
}

export function fetchDatasetDetail(id) {
  return request(`/datasets/${encodeURIComponent(id)}`)
}

export function fetchDatasetStats(id) {
  return request(`/datasets/${encodeURIComponent(id)}/stats`)
}

export function fetchQualityOverview() {
  return request('/quality/overview')
}

export function fetchQualityRuns() {
  return request('/quality/runs')
}

export function fetchQualityRun(runId) {
  return request(`/quality/runs/${encodeURIComponent(runId)}`)
}

export function fetchDatasetQualitySummary(datasetId) {
  return request(`/quality/datasets/${encodeURIComponent(datasetId)}/summary`)
}

export function fetchDatasetQualityIssues(datasetId) {
  return request(`/quality/datasets/${encodeURIComponent(datasetId)}/issues`)
}

export function fetchDatasetFieldDictionary(datasetId) {
  return request(`/quality/datasets/${encodeURIComponent(datasetId)}/field-dictionary`)
}

export function fetchDatasetQualityReport(datasetId) {
  return request(`/quality/datasets/${encodeURIComponent(datasetId)}/report`)
}

export function reviewQualityIssue(issueId, payload) {
  return request(`/quality/issues/${encodeURIComponent(issueId)}/review`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  })
}

export function submitQualityRunReview(runId, payload) {
  return request(`/quality/runs/${encodeURIComponent(runId)}/submit-review`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function publishQualityDecision(datasetId, payload) {
  return request(`/quality/datasets/${encodeURIComponent(datasetId)}/publish-decision`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function validateDatasetLinks(payload) {
  return request('/quality/validate-links', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function previewDatasetFile(file) {
  const form = new FormData()
  form.set('file', file)
  const response = await fetch(`${BASE_URL}/quality/preview-file`, {
    method: 'POST',
    headers: authHeaders(),
    body: form
  })
  if (!response.ok) {
    let message = `文件预检失败: ${response.status}`
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      // Keep status fallback.
    }
    throw new Error(message)
  }
  return response.json()
}

export function fetchDatasetPublication() {
  return request('/datasets/publication')
}

export function updateDatasetPublication(datasetId, published, note = '', grade = '', runId = '', decision = '') {
  return request(`/datasets/${encodeURIComponent(datasetId)}/publication`, {
    method: 'PATCH',
    body: JSON.stringify({ published, note, grade, runId, decision })
  })
}

export function fetchDatasetRecords(
  id,
  { search = '', offset = 0, limit = 24, energyMin = '', energyMax = '', atomMin = '', atomMax = '' } = {}
) {
  const params = new URLSearchParams()
  if (search) params.set('search', search)
  if (energyMin !== '') params.set('energyMin', energyMin)
  if (energyMax !== '') params.set('energyMax', energyMax)
  if (atomMin !== '') params.set('atomMin', atomMin)
  if (atomMax !== '') params.set('atomMax', atomMax)
  params.set('offset', offset)
  params.set('limit', limit)
  const query = params.toString()
  return request(`/datasets/${encodeURIComponent(id)}/records${query ? `?${query}` : ''}`)
}

export function fetchDatasetRecord(datasetId, recordId) {
  return request(`/datasets/${encodeURIComponent(datasetId)}/records/${encodeURIComponent(recordId)}`)
}

export function fetchGroupDetail(datasetId, groupId) {
  return request(`/datasets/${encodeURIComponent(datasetId)}/groups/${encodeURIComponent(groupId)}`)
}

export function fetchConformer(datasetId, groupId, index) {
  return request(
    `/datasets/${encodeURIComponent(datasetId)}/groups/${encodeURIComponent(groupId)}/conformers/${index}`
  )
}

export function conformerDownloadUrl(datasetId, groupId, index) {
  return `${BASE_URL}/datasets/${encodeURIComponent(datasetId)}/groups/${encodeURIComponent(groupId)}/conformers/${index}/download.csv`
}

export function recordDownloadUrl(datasetId, recordId) {
  return `${BASE_URL}/datasets/${encodeURIComponent(datasetId)}/records/${encodeURIComponent(recordId)}/download.csv`
}

export async function login(credentials) {
  const session = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials)
  })
  setSession(session)
  return session
}

export async function register(account) {
  const session = await request('/auth/register', {
    method: 'POST',
    body: JSON.stringify(account)
  })
  setSession(session)
  return session
}

export async function fetchMe() {
  const user = await request('/auth/me')
  if (!user.authenticated) clearSession()
  return user
}

export async function logout() {
  await request('/auth/logout', { method: 'POST' })
  clearSession()
}

export function fetchUsers() {
  return request('/auth/users')
}

export function updateUserRole(username, role) {
  return request(`/auth/users/${encodeURIComponent(username)}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role })
  })
}

export function chatWithAssistant(payload) {
  return request('/assistant/chat', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function fetchPublicSources() {
  return request('/intake/sources')
}

export function fetchDiscoverySources() {
  return request('/intake/discovery/sources')
}

export function fetchDiscoveryConfig() {
  return request('/intake/discovery/config')
}

export function updateDiscoveryConfig(payload) {
  return request('/intake/discovery/config', {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function fetchDiscoveryRuns() {
  return request('/intake/discovery/runs')
}

export function runDatasetDiscovery() {
  return request('/intake/discovery/run', {
    method: 'POST'
  })
}

export function fetchDiscoveryCandidates() {
  return request('/intake/discovery/candidates')
}

export function reviewDiscoveryCandidate(id, decision, note = '') {
  return request(`/intake/discovery/candidates/${encodeURIComponent(id)}/review`, {
    method: 'PATCH',
    body: JSON.stringify({ decision, note })
  })
}

export function promoteDiscoveryCandidate(id) {
  return request(`/intake/discovery/candidates/${encodeURIComponent(id)}/promote`, {
    method: 'POST'
  })
}

export function validateDiscoveryCandidate(id) {
  return request(`/intake/discovery/candidates/${encodeURIComponent(id)}/validate`, {
    method: 'POST'
  })
}

export function fetchDatasetSubmissions() {
  return request('/intake/submissions')
}

export function submitDatasetSource(payload) {
  return request('/intake/submissions', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function reviewDatasetSubmission(id, decision, note) {
  return request(`/intake/submissions/${encodeURIComponent(id)}/review`, {
    method: 'PATCH',
    body: JSON.stringify({ decision, note })
  })
}

export function prepareDatasetPipeline(id) {
  return request(`/intake/submissions/${encodeURIComponent(id)}/prepare`, {
    method: 'POST'
  })
}

export function adaptDatasetSubmission(id) {
  return request(`/intake/submissions/${encodeURIComponent(id)}/adapt`, {
    method: 'POST'
  })
}

export function saveDatasetSubmissionSource(id) {
  return request(`/intake/submissions/${encodeURIComponent(id)}/save-source`, {
    method: 'POST'
  })
}

export function fetchIngestSuggestion(id) {
  return request(`/intake/submissions/${encodeURIComponent(id)}/ingest-suggestion`)
}

export function ingestSubmission(id, payload) {
  return request(`/intake/submissions/${encodeURIComponent(id)}/ingest`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function withdrawSubmission(id) {
  return request(`/intake/submissions/${encodeURIComponent(id)}/withdraw`, {
    method: 'POST'
  })
}

export function downloadRecord(datasetId, recordId) {
  return downloadFile(
    `/datasets/${encodeURIComponent(datasetId)}/records/${encodeURIComponent(recordId)}/download.json`,
    `${datasetId}-record-${recordId}.json`
  )
}

export function downloadDataset(datasetId) {
  return downloadFile(
    `/datasets/${encodeURIComponent(datasetId)}/download.csv`,
    `${datasetId}-all-display-records.csv`
  )
}

async function downloadFile(path, fallbackFilename) {
  const response = await fetch(`${BASE_URL}${path}`, { headers: authHeaders() })
  if (!response.ok) {
    let message = `下载失败: ${response.status}`
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      // Use status fallback for non-JSON responses.
    }
    throw new Error(message)
  }
  const disposition = response.headers.get('Content-Disposition') || ''
  const matched = disposition.match(/filename\*?=(?:UTF-8''|\"?)([^\";]+)/i)
  const filename = matched ? decodeURIComponent(matched[1].replace(/\"/g, '')) : fallbackFilename
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
