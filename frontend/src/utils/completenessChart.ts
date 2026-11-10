import type { EChartsCoreOption } from 'echarts/core'

import type { DimensionScore } from '@/api/profile'

const PRIMARY = '#2563eb'
const INK_SECONDARY = '#52514e'
const GRID = '#e5e7eb'
const SURFACE = '#ffffff'
const SURFACE_ALT = '#f8fafc'

/**
 * 档案完整度雷达图（单系列）：
 * - 6 个维度固定 max=100，分值直接来自后端；
 * - 单系列不配图例，维度名由雷达轴承载，具体分值在页面列表中给出；
 * - 2px 折线 + 半透明面积，避免大面积高饱和填充。
 */
export function buildCompletenessRadarOption(
  dimensions: DimensionScore[],
): EChartsCoreOption {
  return {
    aria: { enabled: true },
    tooltip: {
      trigger: 'item',
      formatter: () =>
        dimensions.map((item) => `${item.name}：${item.score}`).join('<br/>'),
    },
    radar: {
      indicator: dimensions.map((item) => ({ name: item.name, max: 100 })),
      center: ['50%', '54%'],
      radius: '58%',
      splitNumber: 4,
      axisName: {
        color: INK_SECONDARY,
        fontSize: 12,
        // 「工作/实习经历」等长维度名换行，避免窄卡片里被裁切
        formatter: (name: string) =>
          name.length > 6 ? `${name.slice(0, 5)}\n${name.slice(5)}` : name,
      },
      axisLine: { lineStyle: { color: GRID } },
      splitLine: { lineStyle: { color: GRID } },
      splitArea: { areaStyle: { color: [SURFACE, SURFACE_ALT] } },
    },
    series: [
      {
        type: 'radar',
        name: '完整度',
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { width: 2, color: PRIMARY },
        itemStyle: { color: PRIMARY, borderColor: SURFACE, borderWidth: 2 },
        areaStyle: { color: 'rgba(37, 99, 235, 0.16)' },
        emphasis: { lineStyle: { width: 3 } },
        data: [
          {
            value: dimensions.map((item) => item.score),
            name: '完整度',
          },
        ],
      },
    ],
  }
}
