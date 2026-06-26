<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  LinearScale,
  Tooltip
} from 'chart.js'

Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip)

const props = defineProps({
  stats: {
    type: Object,
    default: null
  },
  accent: {
    type: String,
    default: '#2f6fed'
  }
})

const atomCanvas = ref(null)
const energyCanvas = ref(null)
const gapCanvas = ref(null)
const charts = new Map()

const chartCards = computed(() => [
  {
    key: 'atom',
    title: '原子数分布',
    note: '结构规模',
    bins: props.stats?.atomCountHistogram || [],
    canvas: atomCanvas
  },
  {
    key: 'energy',
    title: '能量分布',
    note: '原始单位',
    bins: props.stats?.energyHistogram || [],
    canvas: energyCanvas
  },
  {
    key: 'gap',
    title: 'Band gap / Gap',
    note: 'eV 或原始记录',
    bins: props.stats?.gapHistogram || [],
    canvas: gapCanvas
  }
])

const elementCounts = computed(() => {
  const items = props.stats?.elementCounts || []
  const max = Math.max(...items.map(item => item.count), 1)
  return items.slice(0, 14).map(item => ({
    ...item,
    width: `${Math.max(8, (item.count / max) * 100)}%`
  }))
})

const availability = computed(() => props.stats?.availability || [])

watch(
  () => [props.stats, props.accent],
  async () => {
    await nextTick()
    renderCharts()
  },
  { deep: true, immediate: true }
)

onBeforeUnmount(() => {
  destroyCharts()
})

function renderCharts() {
  chartCards.value.forEach(card => {
    if (!card.bins.length || !card.canvas.value) {
      destroyChart(card.key)
      return
    }
    destroyChart(card.key)
    charts.set(card.key, new Chart(card.canvas.value, {
      type: 'bar',
      data: {
        labels: card.bins.map(bin => bin.label),
        datasets: [{
          label: card.title,
          data: card.bins.map(bin => bin.count),
          borderWidth: 1,
          borderColor: props.accent,
          backgroundColor: hexToRgba(props.accent, 0.18),
          hoverBackgroundColor: hexToRgba(props.accent, 0.28),
          borderRadius: 6
        }]
      },
      options: chartOptions()
    }))
  })
}

function chartOptions() {
  const styles = getComputedStyle(document.documentElement)
  const text = styles.getPropertyValue('--vs-text-secondary').trim() || '#52636f'
  const grid = styles.getPropertyValue('--vs-border').trim() || '#e3eaf3'
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        displayColors: false,
        callbacks: {
          title: items => items[0]?.label || '',
          label: item => `记录数：${Number(item.raw || 0).toLocaleString('zh-CN')}`
        }
      }
    },
    scales: {
      x: {
        ticks: { color: text, maxRotation: 0, autoSkip: true, maxTicksLimit: 5 },
        grid: { display: false }
      },
      y: {
        ticks: { color: text, precision: 0 },
        grid: { color: grid }
      }
    }
  }
}

function destroyCharts() {
  Array.from(charts.keys()).forEach(destroyChart)
}

function destroyChart(key) {
  const chart = charts.get(key)
  if (chart) {
    chart.destroy()
    charts.delete(key)
  }
}

function hexToRgba(hex, alpha) {
  const normalized = hex.replace('#', '')
  const value = normalized.length === 3
    ? normalized.split('').map(ch => ch + ch).join('')
    : normalized
  const number = Number.parseInt(value, 16)
  if (!Number.isFinite(number)) return `rgba(47, 111, 237, ${alpha})`
  const r = (number >> 16) & 255
  const g = (number >> 8) & 255
  const b = number & 255
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
</script>

<template>
  <section v-if="stats" class="stats-charts" :style="{ '--accent': accent }">
    <div class="charts-head">
      <div>
        <p>Property overview</p>
        <h2>材料性质图表</h2>
      </div>
      <span>{{ Number(stats.total || 0).toLocaleString('zh-CN') }} records</span>
    </div>

    <div class="chart-grid">
      <article v-if="chartCards[0].bins.length" class="chart-card">
        <div class="chart-title">
          <strong>{{ chartCards[0].title }}</strong>
          <span>{{ chartCards[0].note }}</span>
        </div>
        <div class="canvas-wrap">
          <canvas ref="atomCanvas"></canvas>
        </div>
      </article>

      <article v-if="chartCards[1].bins.length" class="chart-card">
        <div class="chart-title">
          <strong>{{ chartCards[1].title }}</strong>
          <span>{{ chartCards[1].note }}</span>
        </div>
        <div class="canvas-wrap">
          <canvas ref="energyCanvas"></canvas>
        </div>
      </article>

      <article v-if="chartCards[2].bins.length" class="chart-card">
        <div class="chart-title">
          <strong>{{ chartCards[2].title }}</strong>
          <span>{{ chartCards[2].note }}</span>
        </div>
        <div class="canvas-wrap">
          <canvas ref="gapCanvas"></canvas>
        </div>
      </article>

      <article v-if="elementCounts.length" class="chart-card element-card">
        <div class="chart-title">
          <strong>元素组成</strong>
          <span>Top elements</span>
        </div>
        <div class="element-list">
          <div v-for="item in elementCounts" :key="item.element" class="element-row">
            <span>{{ item.element }}</span>
            <div><i :style="{ width: item.width }"></i></div>
            <strong>{{ item.count.toLocaleString('zh-CN') }}</strong>
          </div>
        </div>
      </article>
    </div>

    <div class="availability">
      <span v-for="item in availability" :key="item.key">
        {{ item.label }}：{{ Number(item.count || 0).toLocaleString('zh-CN') }}
      </span>
    </div>
  </section>
</template>

<style scoped>
.stats-charts {
  margin-top: 16px;
  padding: 18px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
}

.charts-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.charts-head p {
  margin: 0 0 5px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.charts-head h2 {
  margin: 0;
  font-size: 18px;
}

.charts-head > span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
  font-weight: 800;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.chart-card {
  min-height: 230px;
  padding: 14px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
}

.chart-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.chart-title strong {
  color: var(--vs-text);
  font-size: 14px;
}

.chart-title span {
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

.canvas-wrap {
  height: 168px;
}

.element-list {
  display: grid;
  gap: 9px;
}

.element-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 64px;
  align-items: center;
  gap: 9px;
}

.element-row span,
.element-row strong {
  color: var(--vs-text);
  font-size: 12px;
  font-weight: 900;
}

.element-row div {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--vs-border) 74%, transparent);
}

.element-row i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent), color-mix(in srgb, var(--accent) 36%, var(--vs-card)));
}

.availability {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.availability span {
  padding: 5px 9px;
  border: 1px solid color-mix(in srgb, var(--accent) 20%, var(--vs-border));
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 7%, var(--vs-card));
  color: var(--vs-text-secondary);
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 1280px) {
  .chart-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .charts-head {
    display: block;
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
