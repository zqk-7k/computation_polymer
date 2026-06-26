<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  categories: {
    type: Array,
    default: () => []
  }
})

const activeKey = ref('')

watch(
  () => props.categories,
  value => {
    if (!value.length) {
      activeKey.value = ''
      return
    }
    if (!value.some(item => item.key === activeKey.value)) {
      activeKey.value = value[0].key
    }
  },
  { immediate: true }
)

const activeCategory = computed(() => props.categories.find(item => item.key === activeKey.value) || props.categories[0])
</script>

<template>
  <section class="coverage-panel">
    <div class="panel-head">
      <div>
        <span>Coverage matrix</span>
        <h2>数据集覆盖范围</h2>
      </div>
      <p>按研究对象和建模用途组织，便于专家快速判断数据域、规模和可用标签。</p>
    </div>

    <div class="coverage-grid">
      <button
        v-for="category in categories"
        :key="category.key"
        type="button"
        class="coverage-card"
        :class="{ active: category.key === activeKey }"
        @click="activeKey = category.key"
      >
        <span>{{ category.code }}</span>
        <strong>{{ category.title }}</strong>
        <small>{{ category.count }} 个数据集 · {{ category.totalLabel }}</small>
        <em>{{ category.description }}</em>
      </button>
    </div>

    <div v-if="activeCategory" class="coverage-detail">
      <div>
        <span>当前选择</span>
        <strong>{{ activeCategory.title }}</strong>
      </div>
      <p>{{ activeCategory.detail }}</p>
      <small>{{ activeCategory.datasetNames }}</small>
    </div>
  </section>
</template>

<style scoped>
.coverage-panel {
  padding: 20px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-lg);
  background: var(--vs-card);
  box-shadow: var(--vs-shadow-sm);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;
}

.panel-head span,
.coverage-detail span {
  display: block;
  color: var(--vs-blue-600);
  font-size: var(--vs-type-xs);
  font-weight: 900;
  text-transform: uppercase;
}

.panel-head h2 {
  margin: 6px 0 0;
  font-size: 20px;
}

.panel-head p {
  max-width: 520px;
  margin: 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: var(--vs-leading-normal);
}

.coverage-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.coverage-card {
  position: relative;
  min-height: 118px;
  padding: 13px;
  border: 1px solid color-mix(in srgb, var(--vs-blue-300) 36%, var(--vs-border));
  border-radius: var(--vs-radius-md);
  background:
    linear-gradient(180deg, var(--vs-card), color-mix(in srgb, var(--vs-blue-100) 24%, var(--vs-card)));
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: transform 0.16s ease, border-color 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.coverage-card:hover,
.coverage-card.active {
  border-color: color-mix(in srgb, var(--vs-cyan-500) 56%, var(--vs-blue-300));
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--vs-cyan-100) 34%, var(--vs-card)), var(--vs-card));
  box-shadow: 0 12px 28px rgba(8, 146, 165, 0.12);
  transform: translateY(-2px);
}

.coverage-card.active::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  border-radius: var(--vs-radius-md) var(--vs-radius-md) 0 0;
  background: linear-gradient(90deg, var(--vs-blue-500), var(--vs-cyan-500));
}

.coverage-card span {
  display: inline-flex;
  min-width: 36px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-blue-500) 12%, var(--vs-card));
  color: var(--vs-blue-600);
  font-size: 11px;
  font-weight: 950;
}

.coverage-card strong,
.coverage-card small,
.coverage-card em {
  display: block;
}

.coverage-card strong {
  margin-top: 11px;
  color: var(--vs-text);
  font-size: 14px;
}

.coverage-card small {
  margin-top: 6px;
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: 1.35;
}

.coverage-card em {
  margin-top: 8px;
  color: var(--vs-text-tertiary);
  font-size: 10.5px;
  font-style: normal;
  line-height: 1.45;
}

.coverage-detail {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) minmax(220px, 0.75fr);
  gap: 14px;
  align-items: center;
  margin-top: 14px;
  padding: 13px;
  border: 1px solid color-mix(in srgb, var(--vs-cyan-500) 28%, var(--vs-border));
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-cyan-100) 24%, var(--vs-card));
}

.coverage-detail strong {
  display: block;
  margin-top: 5px;
  color: var(--vs-text);
  font-size: 16px;
}

.coverage-detail p,
.coverage-detail small {
  margin: 0;
  color: var(--vs-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.coverage-detail small {
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .coverage-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .panel-head,
  .coverage-detail {
    display: grid;
    grid-template-columns: 1fr;
  }

  .coverage-grid {
    grid-template-columns: 1fr;
  }
}
</style>
