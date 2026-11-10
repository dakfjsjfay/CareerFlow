<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import {
  AriaComponent,
  RadarComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

import type { DimensionScore } from '@/api/profile'
import { buildCompletenessRadarOption } from '@/utils/completenessChart'

echarts.use([
  RadarChart,
  RadarComponent,
  TooltipComponent,
  AriaComponent,
  CanvasRenderer,
])

const props = defineProps<{
  overall: number
  dimensions: DimensionScore[]
}>()

const chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function render(): void {
  // ECharts 雷达图不支持空 indicator；等后端返回 6 个维度后再初始化
  if (!chartEl.value || props.dimensions.length === 0) {
    return
  }
  chart ??= echarts.init(chartEl.value)
  chart.setOption(buildCompletenessRadarOption(props.dimensions))
}

function handleResize(): void {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
})

watch(
  () => props.dimensions,
  (dimensions) => {
    if (!dimensions.length) {
      chart?.clear()
      return
    }
    render()
  },
  { deep: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="completeness-radar">
    <div class="overall">
      <span class="overall-label">档案完整度</span>
      <p class="overall-value">
        {{ overall }}<span class="overall-unit">%</span>
      </p>
    </div>
    <div class="chart-wrap">
      <div
        ref="chartEl"
        data-testid="radar-chart"
        class="radar-chart"
        role="img"
        aria-label="档案完整度雷达图"
      />
      <p v-if="!dimensions.length" class="radar-placeholder">保存档案后生成完整度</p>
    </div>
  </div>
</template>

<style scoped>
.completeness-radar {
  position: relative;
}

.overall {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 4px;
}

.overall-label {
  font-size: 13px;
  color: var(--cf-text-secondary);
}

.overall-value {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  color: var(--cf-text);
  letter-spacing: -0.02em;
}

.overall-unit {
  margin-left: 2px;
  font-size: 14px;
  font-weight: 500;
  color: var(--cf-text-secondary);
}

.chart-wrap {
  position: relative;
}

.radar-chart {
  width: 100%;
  height: 280px;
}

.radar-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  font-size: 12px;
  color: var(--cf-text-secondary);
  pointer-events: none;
}
</style>
