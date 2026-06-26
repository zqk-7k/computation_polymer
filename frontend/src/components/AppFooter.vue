<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function go(name) {
  router.push({ name })
}
</script>

<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <div class="footer-brand">
        <strong>VASP Show</strong>
        <p>分子、聚合物与材料计算数据平台</p>
      </div>
      <div class="footer-group">
        <span>数据服务</span>
        <button type="button" @click="go('explore')">数据发现</button>
        <button type="button" @click="go('intake')">数据合作与接入</button>
      </div>
      <div class="footer-group">
        <span>支持</span>
        <button type="button" @click="go('assistant')">技术支持助手</button>
        <button type="button" @click="go('login')">登录与权限</button>
      </div>
      <div class="footer-contact">
        <span>联系我们</span>
        <strong>数据来源、引用纠错与合作接入</strong>
        <button class="contact-action" type="button" @click="go('intake')">提交合作申请</button>
      </div>
    </div>
    <div class="footer-base">VASP Show · Computational Materials Data Platform</div>
  </footer>
</template>

<style scoped>
.app-footer {
  border-top: 1px solid var(--vs-border);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  font-family: Inter, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.footer-inner {
  width: min(1880px, calc(100% - 48px));
  margin: 0 auto;
  padding: 32px 0 26px;
  display: grid;
  grid-template-columns: minmax(230px, 1.6fr) minmax(140px, 1fr) minmax(140px, 1fr) minmax(260px, 1.2fr);
  gap: 32px;
}

.footer-brand strong {
  display: block;
  margin-bottom: 8px;
  color: var(--vs-text);
  font-size: 19px;
  font-weight: 900;
}

.footer-brand p,
.footer-contact p {
  margin: 0;
  color: var(--vs-text-tertiary);
  font-size: 13px;
  line-height: 1.65;
}

.footer-group,
.footer-contact {
  display: grid;
  align-content: start;
  gap: 10px;
}

.footer-group span,
.footer-contact span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 800;
}

.footer-group button {
  width: fit-content;
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--vs-text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.footer-group button:hover {
  color: var(--vs-primary);
}

.footer-contact strong {
  color: var(--vs-text);
  font-size: 13px;
}

.contact-action {
  width: fit-content;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-primary) 9%, var(--vs-card));
  color: var(--vs-primary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
}

.footer-base {
  border-top: 1px solid var(--vs-border);
  padding: 16px 24px;
  color: var(--vs-text-tertiary);
  text-align: center;
  font-size: 12px;
}

@media (max-width: 860px) {
  .footer-inner {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .footer-inner {
    width: min(100% - 32px, 1880px);
    grid-template-columns: 1fr;
  }
}
</style>
