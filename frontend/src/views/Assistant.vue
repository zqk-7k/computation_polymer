<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chatWithAssistant } from '../api'
import { authUser, isAuthenticated } from '../auth/session'
import AppTopbar from '../components/AppTopbar.vue'

const route = useRoute()
const router = useRouter()
const draft = ref('')
const sending = ref(false)
const error = ref('')
const responseModel = ref('')
let messageSequence = 1

const datasetId = computed(() => String(route.query.datasetId || '').trim())
const recordId = computed(() => {
  const value = Number(route.query.recordId)
  return Number.isInteger(value) && value > 0 ? value : null
})
const contextName = computed(() => String(route.query.contextName || datasetId.value || '全部数据集目录'))
const contextType = computed(() => recordId.value ? '当前结构记录' : (datasetId.value ? '当前数据集' : '数据集目录'))
const suggestedQuestions = computed(() => {
  if (recordId.value) {
    return ['概述这个记录已提供的性质字段', '这个结构的能量和计算方法是什么？', '当前记录缺少哪些常用字段？']
  }
  if (datasetId.value) {
    return ['这个数据集包含哪些可用性质？', '该数据集适合什么研究任务？', '说明其计算方法、泛函和基组']
  }
  return ['推荐包含 Band gap 的 VASP 数据集', '哪些数据集提供三维坐标和能量？', '用于聚合物研究的数据集有哪些？']
})

const messages = ref([welcomeMessage()])

function welcomeMessage() {
  return {
    id: messageSequence++,
    role: 'assistant',
    local: true,
    content: recordId.value
      ? '已载入当前结构记录，可直接询问它的已提供字段和计算信息。'
      : (datasetId.value ? '已载入当前数据集，可直接询问其字段、规模与计算设置。' : '请选择问题，助手将依据平台中已接入的数据集回答。')
  }
}

function goHome(tab = 'data') {
  router.push({ name: 'home', query: tab === 'data' ? {} : { tab } })
}

function openExplore() {
  router.push({ name: 'explore' })
}

function openLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

function backToContext() {
  if (recordId.value) {
    router.push({ name: 'record-detail', params: { id: datasetId.value, recordId: recordId.value } })
    return
  }
  if (datasetId.value) {
    router.push({ name: 'dataset-records', params: { id: datasetId.value } })
    return
  }
  openExplore()
}

function newConversation() {
  messages.value = [welcomeMessage()]
  error.value = ''
  responseModel.value = ''
}

function askSuggested(question) {
  draft.value = question
  submitQuestion()
}

async function submitQuestion() {
  const question = draft.value.trim()
  if (!question || sending.value) return
  if (!isAuthenticated.value) {
    openLogin()
    return
  }
  messages.value.push({ id: messageSequence++, role: 'user', content: question })
  draft.value = ''
  error.value = ''
  sending.value = true
  try {
    const response = await chatWithAssistant({
      datasetId: datasetId.value || null,
      recordId: recordId.value,
      messages: messages.value
        .filter(message => !message.local)
        .slice(-12)
        .map(message => ({ role: message.role, content: message.content }))
    })
    responseModel.value = response.model
    messages.value.push({
      id: messageSequence++,
      role: 'assistant',
      content: response.answer,
      sources: response.groundedSources,
      contextLabel: response.contextLabel
    })
  } catch (err) {
    error.value = err.message || '智能助手暂时无法回答'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="page">
    <AppTopbar show-back @brand-click="goHome('data')">
      <nav class="main-nav">
        <button @click="goHome('data')">数据中心</button>
        <button @click="openExplore">数据发现</button>
        <button @click="goHome('quality')">质量验证</button>
        <button class="active">智能助手</button>
        <button @click="goHome('model')">模型</button>
        <button @click="goHome('workflow')">工作流</button>
      </nav>
    </AppTopbar>

    <main class="shell">
      <header class="heading">
        <div>
          <p class="eyebrow">Data Assistant</p>
          <h1>智能助手</h1>
        </div>
        <div class="service-state">
          <i></i>
          <span>{{ responseModel || 'Ollama / qwen3:8b' }}</span>
        </div>
      </header>

      <section class="workspace">
        <aside class="context-panel">
          <div class="context-head">
            <span>受控数据上下文</span>
            <button type="button" @click="backToContext">查看来源</button>
          </div>
          <strong>{{ contextName }}</strong>
          <p>{{ contextType }}</p>

          <div class="context-rule">
            <span>回答依据</span>
            <p>当前目录及已载入记录中的真实字段</p>
          </div>

          <div class="prompts">
            <span>建议问题</span>
            <button
              v-for="question in suggestedQuestions"
              :key="question"
              type="button"
              @click="askSuggested(question)"
            >{{ question }}</button>
          </div>
        </aside>

        <article class="chat-panel">
          <div class="chat-toolbar">
            <div>
              <strong>{{ contextType }}</strong>
              <span>{{ contextName }}</span>
            </div>
            <button type="button" @click="newConversation">新对话</button>
          </div>

          <div class="messages">
            <div
              v-for="message in messages"
              :key="message.id"
              class="message"
              :class="message.role"
            >
              <span class="message-role">{{ message.role === 'user' ? authUser.displayName : '助手' }}</span>
              <p>{{ message.content }}</p>
              <div v-if="message.sources?.length" class="sources">
                <span>依据</span>
                <em v-for="source in message.sources" :key="source">{{ source }}</em>
              </div>
            </div>
            <div v-if="sending" class="message assistant waiting">
              <span class="message-role">助手</span>
              <p>正在读取数据上下文并生成回答...</p>
            </div>
          </div>

          <p v-if="error" class="error">{{ error }}</p>
          <form class="composer" @submit.prevent="submitQuestion">
            <textarea
              v-model="draft"
              rows="3"
              :disabled="sending"
              placeholder="输入关于数据集、计算字段或当前结构的问题"
              @keydown.ctrl.enter.prevent="submitQuestion"
            ></textarea>
            <div class="composer-actions">
              <span v-if="!isAuthenticated">登录后可提问</span>
              <span v-else>{{ authUser.displayName }}</span>
              <button v-if="!isAuthenticated" type="button" @click="openLogin">登录</button>
              <button v-else type="submit" :disabled="sending || !draft.trim()">发送</button>
            </div>
          </form>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

button,
textarea {
  font: inherit;
}

button {
  cursor: pointer;
}

.shell {
  width: min(1520px, calc(100% - 56px));
  margin: 0 auto;
  padding: 30px 0 52px;
}

.heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 34px;
  letter-spacing: 0;
}

.service-state {
  display: inline-flex;
  height: 38px;
  align-items: center;
  gap: 9px;
  padding: 0 13px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.service-state i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #31a46c;
}

.workspace {
  display: grid;
  grid-template-columns: 290px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.context-panel,
.chat-panel {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
}

.context-panel {
  padding: 18px;
}

.context-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
}

.context-head span,
.prompts > span,
.context-rule span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 800;
}

.context-head button,
.chat-toolbar button {
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-primary);
  padding: 6px 9px;
  font-size: 12px;
  font-weight: 800;
}

.context-panel > strong {
  display: block;
  color: var(--vs-text);
  font-size: 16px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.context-panel > p {
  margin: 6px 0 0;
  color: var(--vs-text-secondary);
  font-size: 13px;
}

.context-rule {
  margin-top: 18px;
  padding: 12px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.context-rule p {
  margin: 7px 0 0;
  color: var(--vs-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.prompts {
  display: grid;
  gap: 8px;
  margin-top: 20px;
}

.prompts button {
  padding: 10px 11px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  text-align: left;
  font-size: 13px;
  line-height: 1.48;
}

.prompts button:hover {
  border-color: var(--vs-border-strong);
  color: var(--vs-primary);
}

.chat-panel {
  min-height: calc(100vh - 166px);
  display: grid;
  grid-template-rows: auto minmax(300px, 1fr) auto auto;
}

.chat-toolbar {
  padding: 15px 18px;
  border-bottom: 1px solid var(--vs-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.chat-toolbar strong,
.chat-toolbar span {
  display: block;
}

.chat-toolbar strong {
  color: var(--vs-text);
  font-size: 14px;
}

.chat-toolbar span {
  margin-top: 4px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.messages {
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
  padding: 22px;
}

.message {
  width: min(760px, 88%);
}

.message.user {
  align-self: flex-end;
}

.message-role {
  display: block;
  margin: 0 0 6px 5px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.message p {
  margin: 0;
  padding: 13px 15px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
  color: var(--vs-text);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.message.user p {
  border-color: color-mix(in srgb, var(--vs-primary) 30%, var(--vs-border));
  background: color-mix(in srgb, var(--vs-primary) 9%, var(--vs-card));
}

.message.waiting p {
  color: var(--vs-text-secondary);
}

.sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 7px;
  padding-left: 5px;
}

.sources span {
  color: var(--vs-text-tertiary);
  font-size: 11px;
}

.sources em {
  border: 1px solid var(--vs-border);
  border-radius: 999px;
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  padding: 3px 8px;
  font-size: 11px;
  font-style: normal;
}

.error {
  margin: 0 18px 12px;
  padding: 10px 12px;
  border: 1px solid #f1caca;
  border-radius: var(--vs-radius-sm);
  background: #fff3f3;
  color: #cc5656;
  font-size: 13px;
}

.composer {
  border-top: 1px solid var(--vs-border);
  padding: 16px 18px;
}

.composer textarea {
  width: 100%;
  resize: none;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
  color: var(--vs-text);
  padding: 12px 13px;
  line-height: 1.6;
}

.composer textarea:focus {
  outline: none;
  box-shadow: var(--vs-focus);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 10px;
}

.composer-actions span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.composer-actions button {
  height: 36px;
  padding: 0 18px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  font-weight: 800;
}

.composer-actions button:disabled {
  opacity: 0.5;
  cursor: wait;
}

@media (max-width: 900px) {
  .shell {
    width: calc(100% - 32px);
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  .context-panel {
    order: 1;
  }

  .chat-panel {
    min-height: 620px;
  }
}
</style>
