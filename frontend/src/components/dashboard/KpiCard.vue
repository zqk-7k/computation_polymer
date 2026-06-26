<script setup>
defineProps({
  label: {
    type: String,
    required: true
  },
  value: {
    type: [String, Number],
    required: true
  },
  note: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <article class="kpi-card">
    <span>{{ label }}</span>
    <strong>{{ value }}</strong>
    <small v-if="note">{{ note }}</small>
  </article>
</template>

<style scoped>
.kpi-card {
  position: relative;
  overflow: hidden;
  min-height: 92px;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--vs-blue-300) 58%, var(--vs-border));
  border-radius: var(--vs-radius-md);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--vs-card) 88%, transparent), color-mix(in srgb, var(--vs-blue-100) 28%, var(--vs-card)));
  box-shadow: var(--vs-shadow-glow-blue);
  backdrop-filter: blur(var(--vs-glass-blur));
}

.kpi-card::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  background: linear-gradient(90deg, var(--vs-blue-500), var(--vs-cyan-500), transparent);
}

.kpi-card span,
.kpi-card strong,
.kpi-card small {
  display: block;
}

.kpi-card span {
  color: var(--vs-text-tertiary);
  font-size: 11px;
  font-weight: 850;
}

.kpi-card strong {
  margin-top: 9px;
  color: var(--vs-blue-600);
  font-family: var(--vs-font-sans);
  font-size: clamp(26px, 1.85vw, 34px);
  line-height: 1;
  letter-spacing: 0;
}

.kpi-card small {
  margin-top: 8px;
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: 1.45;
}
</style>
