<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  elements: {
    type: Array,
    default: () => []
  }
})

const hovered = ref(null)
const tooltip = ref({ x: 0, y: 0 })

const maxCount = computed(() => Math.max(...props.elements.map(item => item.datasetCount || 0), 1))

function barHeight(entry) {
  return `${20 + ((entry.datasetCount || 0) / maxCount.value) * 58}px`
}

function showTooltip(entry, event) {
  hovered.value = entry
  tooltip.value = {
    x: event.clientX,
    y: event.clientY
  }
}

function moveTooltip(event) {
  tooltip.value = {
    x: event.clientX,
    y: event.clientY
  }
}

function hideTooltip() {
  hovered.value = null
}
</script>

<template>
  <section class="element-spectrum">
    <div class="panel-head">
      <div>
        <span>Element spectrum</span>
        <h2>元素覆盖图谱</h2>
      </div>
      <p>统计当前数据库中高频元素的覆盖数据集数量和对应结构规模。</p>
    </div>

    <div class="spectrum-chart" role="img" aria-label="高频元素覆盖柱状图">
      <button
        v-for="entry in elements"
        :key="entry.symbol"
        type="button"
        class="spectrum-bar"
        :class="{ featured: entry.featured }"
        @mouseenter="showTooltip(entry, $event)"
        @mousemove="moveTooltip"
        @mouseleave="hideTooltip"
      >
        <span class="bar-track">
          <i :style="{ height: barHeight(entry) }"></i>
        </span>
        <strong>{{ entry.symbol }}</strong>
      </button>
    </div>

    <div
      v-if="hovered"
      class="spectrum-tooltip"
      :style="{ left: `${tooltip.x + 14}px`, top: `${tooltip.y + 14}px` }"
    >
      <strong>{{ hovered.symbol }}</strong>
      <span>覆盖数据集：{{ hovered.datasetCount }}</span>
      <span>结构规模：{{ hovered.recordLabel }}</span>
    </div>
  </section>
</template>

<style scoped>
.element-spectrum {
  position: relative;
  overflow: hidden;
  padding: 16px 18px 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-lg);
  background:
    radial-gradient(circle at 88% 16%, rgba(22, 181, 200, 0.08), transparent 24%),
    linear-gradient(180deg, var(--vs-card), color-mix(in srgb, var(--vs-blue-100) 16%, var(--vs-card)));
  box-shadow: var(--vs-shadow-sm);
}

.element-spectrum::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(47, 111, 237, 0.055) 1px, transparent 1px),
    linear-gradient(0deg, rgba(8, 146, 165, 0.045) 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: linear-gradient(180deg, transparent, #000 24%, #000 84%, transparent);
  pointer-events: none;
}

.panel-head {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.panel-head span {
  color: var(--vs-blue-600);
  font-size: var(--vs-type-xs);
  font-weight: 900;
  text-transform: uppercase;
}

.panel-head h2 {
  margin: 5px 0 0;
  font-size: 18px;
}

.panel-head p {
  max-width: 480px;
  margin: 0;
  color: var(--vs-text-secondary);
  font-size: 11px;
  line-height: var(--vs-leading-normal);
}

.spectrum-chart {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(18, minmax(0, 1fr));
  gap: 8px;
  align-items: end;
  min-height: 108px;
  padding: 9px 4px 0;
  border-radius: var(--vs-radius-md);
  background:
    linear-gradient(90deg, rgba(47, 111, 237, 0.05), transparent 18%, transparent 82%, rgba(22, 181, 200, 0.05)),
    linear-gradient(180deg, transparent, color-mix(in srgb, var(--vs-blue-100) 24%, transparent)),
    repeating-linear-gradient(0deg, transparent 0, transparent 23px, color-mix(in srgb, var(--vs-border) 42%, transparent) 24px);
}

.spectrum-bar {
  display: grid;
  justify-items: center;
  gap: 7px;
  min-width: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
}

.bar-track {
  position: relative;
  display: flex;
  width: 100%;
  max-width: 19px;
  height: 84px;
  align-items: end;
  justify-content: center;
}

.bar-track i {
  position: relative;
  display: block;
  width: 100%;
  border-radius: 5px 5px 2px 2px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--vs-blue-500) 92%, #ffffff), var(--vs-blue-500) 62%, var(--vs-blue-700));
  box-shadow:
    inset 4px 0 7px rgba(255, 255, 255, 0.24),
    0 8px 14px rgba(47, 111, 237, 0.12);
  transform: skewX(-4deg);
  transition: height 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.bar-track i::after {
  content: "";
  position: absolute;
  inset: -3px 2px auto;
  height: 6px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--vs-blue-300) 74%, #ffffff);
}

.spectrum-bar.featured .bar-track i {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--vs-cyan-500) 84%, #ffffff), var(--vs-cyan-500) 58%, var(--vs-blue-600));
}

.spectrum-bar:hover .bar-track i {
  transform: skewX(-4deg) translateY(-3px);
  box-shadow:
    inset 5px 0 8px rgba(255, 255, 255, 0.28),
    0 16px 24px rgba(8, 146, 165, 0.18);
}

.spectrum-bar strong {
  color: var(--vs-text-secondary);
  font-size: 10.5px;
  line-height: 1;
}

.spectrum-tooltip {
  position: fixed;
  z-index: 40;
  display: grid;
  gap: 5px;
  min-width: 154px;
  padding: 10px 12px;
  border: 1px solid var(--vs-glass-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-glass-bg);
  box-shadow: var(--vs-shadow-md);
  backdrop-filter: blur(var(--vs-glass-blur));
  pointer-events: none;
}

.spectrum-tooltip strong {
  color: var(--vs-blue-600);
  font-size: 15px;
}

.spectrum-tooltip span {
  color: var(--vs-text-secondary);
  font-size: 12px;
}

@media (max-width: 980px) {
  .spectrum-chart {
    grid-template-columns: repeat(9, minmax(0, 1fr));
    row-gap: 18px;
  }
}

@media (max-width: 640px) {
  .panel-head {
    display: grid;
  }

  .spectrum-chart {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}
</style>
