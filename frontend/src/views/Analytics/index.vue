<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import {
  getFeedbackAnalytics,
  type FeedbackAnalytics,
  type AnalyticsSummary,
} from '@/api/analytics'

const router = useRouter()

const EMPTY_SUMMARY: AnalyticsSummary = {
  totalApplications: 0,
  readCount: 0,
  interviewCount: 0,
  offerCount: 0,
  feedbackCount: 0,
  readRate: 0,
  interviewRate: 0,
  offerRate: 0,
}

const loading = ref(false)
const loadFailed = ref(false)
const data = ref<FeedbackAnalytics | null>(null)

const funnelRef = ref<HTMLDivElement>()
const versionRef = ref<HTMLDivElement>()
const feedbackRef = ref<HTMLDivElement>()

let funnelChart: ReturnType<typeof echarts.init> | null = null
let versionChart: ReturnType<typeof echarts.init> | null = null
let feedbackChart: ReturnType<typeof echarts.init> | null = null

const summary = computed(() => data.value?.summary ?? EMPTY_SUMMARY)
const hasData = computed(() => summary.value.totalApplications > 0)
const feedbackHighlights = computed(
  () => data.value?.feedbackDistribution.filter((item) => item.count > 0) ?? [],
)
const hasFeedback = computed(() => feedbackHighlights.value.length > 0)

function percent(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`
}

function chartPercent(rate: number): number {
  return Number((rate * 100).toFixed(1))
}

function errorMessage(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } } }
  return err.response?.data?.message || fallback
}

async function load(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  try {
    const res = await getFeedbackAnalytics()
    data.value = res.data
    await nextTick()
    renderCharts()
  } catch (error) {
    loadFailed.value = true
    ElMessage.error(errorMessage(error, '反馈分析加载失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

function renderCharts(): void {
  disposeCharts()
  if (!hasData.value) {
    return
  }

  if (funnelRef.value) {
    funnelChart = echarts.init(funnelRef.value)
    const funnelOption: EChartsOption = {
      color: ['#2563eb', '#3b82f6', '#60a5fa', '#93c5fd'],
      tooltip: { trigger: 'item', formatter: '{b}: {c} 次' },
      series: [
        {
          type: 'funnel',
          left: '8%',
          width: '84%',
          minSize: '28%',
          maxSize: '100%',
          sort: 'descending',
          gap: 2,
          label: {
            show: true,
            position: 'inside',
            color: '#fff',
            formatter: '{b} {c}',
          },
          itemStyle: { borderColor: '#fff', borderWidth: 1 },
          data: data.value!.funnel.map((stage) => ({
            name: stage.label,
            value: stage.count,
          })),
        },
      ],
    }
    funnelChart.setOption(funnelOption, true)
  }

  if (versionRef.value && data.value!.versionEffects.length > 0) {
    versionChart = echarts.init(versionRef.value)
    const versions = data.value!.versionEffects
    const versionOption: EChartsOption = {
      color: ['#60a5fa', '#2563eb', '#1d4ed8'],
      tooltip: { trigger: 'axis' },
      legend: { top: 0 },
      grid: { top: 40, left: 12, right: 12, bottom: 8, containLabel: true },
      xAxis: {
        type: 'category',
        data: versions.map((item) =>
          item.versionNumber ? `V${item.versionNumber}` : `版本${item.resumeVersionId}`,
        ),
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        max: 100,
        axisLabel: { formatter: '{value}%' },
      },
      series: [
        {
          name: '已读率',
          type: 'bar',
          data: versions.map((item) => chartPercent(item.readRate)),
          itemStyle: { borderRadius: [4, 4, 0, 0] },
        },
        {
          name: '面试率',
          type: 'bar',
          data: versions.map((item) => chartPercent(item.interviewRate)),
          itemStyle: { borderRadius: [4, 4, 0, 0] },
        },
        {
          name: '录用率',
          type: 'bar',
          data: versions.map((item) => chartPercent(item.offerRate)),
          itemStyle: { borderRadius: [4, 4, 0, 0] },
        },
      ],
    }
    versionChart.setOption(versionOption, true)
  }

  if (feedbackRef.value && hasFeedback.value) {
    feedbackChart = echarts.init(feedbackRef.value)
    const feedbackOption: EChartsOption = {
      color: ['#ef4444', '#f59e0b', '#8b5cf6', '#10b981'],
      tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          label: { formatter: '{b}\n{c} 次' },
          data: feedbackHighlights.value.map((item) => ({
            name: item.label,
            value: item.count,
          })),
        },
      ],
    }
    feedbackChart.setOption(feedbackOption, true)
  }
}

function resizeCharts(): void {
  funnelChart?.resize()
  versionChart?.resize()
  feedbackChart?.resize()
}

function disposeCharts(): void {
  funnelChart?.dispose()
  versionChart?.dispose()
  feedbackChart?.dispose()
  funnelChart = null
  versionChart = null
  feedbackChart = null
}

onMounted(() => {
  window.addEventListener('resize', resizeCharts)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})
</script>

<template>
  <div class="analytics-page" data-testid="analytics-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">FEEDBACK ANALYTICS</p>
        <h2>反馈分析</h2>
        <p class="subtitle">
          用投递结果和偏好反馈回看简历效果：漏斗定位卡点，版本对比判断优化是否有效，反馈驱动下一轮推荐。
        </p>
      </div>
      <el-button data-testid="analytics-refresh" :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="metric-grid" v-loading="loading">
      <div class="metric-card">
        <span class="metric-label">总投递</span>
        <strong class="metric-value">{{ summary.totalApplications }}</strong>
        <span class="metric-hint">份简历已投出</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">已读率</span>
        <strong class="metric-value">{{ percent(summary.readRate) }}</strong>
        <span class="metric-hint">{{ summary.readCount }} 份被查看</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">面试率</span>
        <strong class="metric-value">{{ percent(summary.interviewRate) }}</strong>
        <span class="metric-hint">{{ summary.interviewCount }} 份进入面试</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">录用率</span>
        <strong class="metric-value">{{ percent(summary.offerRate) }}</strong>
        <span class="metric-hint">{{ summary.offerCount }} 份拿到录用</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">偏好反馈</span>
        <strong class="metric-value">{{ summary.feedbackCount }}</strong>
        <span class="metric-hint">用于推荐排序</span>
      </div>
    </div>

    <el-card v-if="!loading && loadFailed" shadow="never" class="state-card">
      <el-empty description="分析数据加载失败，请稍后重试">
        <el-button type="primary" @click="load">重新加载</el-button>
      </el-empty>
    </el-card>

    <el-card v-else-if="!loading && !hasData" shadow="never" class="state-card">
      <el-empty description="还没有投递数据，先去岗位中心投递一份简历吧">
        <el-button type="primary" @click="router.push('/jobs')">去岗位中心</el-button>
      </el-empty>
    </el-card>

    <template v-else-if="hasData">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="chart-card">
            <template #header>
              <div class="card-title">
                <span>投递漏斗</span>
                <span class="card-sub">按历史到达的最高阶段累计</span>
              </div>
            </template>
            <div ref="funnelRef" class="chart" data-testid="funnel-chart"></div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="chart-card">
            <template #header>
              <div class="card-title">
                <span>简历版本效果</span>
                <span class="card-sub">不同版本投递后的转化对比</span>
              </div>
            </template>
            <div
              v-if="data && data.versionEffects.length > 0"
              ref="versionRef"
              class="chart"
              data-testid="version-chart"
            ></div>
            <el-empty v-else description="暂无版本对比数据" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="second-row">
        <el-col :xs="24" :md="10">
          <el-card shadow="never" class="chart-card">
            <template #header>
              <div class="card-title">
                <span>偏好反馈分布</span>
                <span class="card-sub">来自岗位中心的反馈</span>
              </div>
            </template>
            <div
              v-if="hasFeedback"
              ref="feedbackRef"
              class="chart chart-pie"
              data-testid="feedback-chart"
            ></div>
            <el-empty v-else description="暂无偏好反馈" :image-size="70" />
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14">
          <el-card shadow="never" class="chart-card impact-card">
            <template #header>
              <div class="card-title">
                <span>反馈如何影响推荐</span>
                <span class="card-sub">可解释规则，非机器学习</span>
              </div>
            </template>
            <ul v-if="hasFeedback" class="impact-list">
              <li v-for="item in feedbackHighlights" :key="item.feedbackType">
                <div class="impact-main">
                  <span class="impact-label">{{ item.label }}</span>
                  <span class="impact-count">{{ item.count }} 次</span>
                </div>
                <span class="impact-bar">
                  <span class="impact-bar-inner" :style="{ width: percent(item.rate) }"></span>
                </span>
              </li>
            </ul>
            <p v-if="hasFeedback" class="impact-note">
              反馈会作为推荐排序的后置规则：不感兴趣、地点或薪资不符的同类岗位后移；技能已掌握的岗位不再重复强调相关技能。
              排序原因会展示在岗位推荐列表中。
            </p>
            <el-empty v-else description="提交岗位反馈后，这里会展示推荐排序依据" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--cf-primary);
  font-weight: 700;
  margin-bottom: 4px;
}

.page-head h2 {
  font-size: 22px;
}

.subtitle {
  margin-top: 6px;
  max-width: 760px;
  color: var(--cf-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
}

.metric-label {
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.metric-value {
  font-size: 26px;
  color: var(--cf-primary-dark);
  line-height: 1.2;
}

.metric-hint {
  font-size: 12px;
  color: var(--cf-text-secondary);
}

.chart-card {
  margin-bottom: 16px;
}

.card-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}

.card-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--cf-text-secondary);
}

.chart {
  height: 300px;
}

.chart-pie {
  height: 280px;
}

.state-card {
  margin-bottom: 16px;
}

.second-row {
  margin-top: 0;
}

.impact-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  list-style: none;
}

.impact-main {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
}

.impact-label {
  font-weight: 600;
}

.impact-count {
  color: var(--cf-text-secondary);
}

.impact-bar {
  display: block;
  height: 8px;
  background: #eff6ff;
  border-radius: 999px;
  overflow: hidden;
}

.impact-bar-inner {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--cf-primary), var(--cf-primary-light));
  border-radius: 999px;
}

.impact-note {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--cf-border);
  font-size: 12px;
  line-height: 1.7;
  color: var(--cf-text-secondary);
}
</style>
