<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listFollowUps, type FollowUpList } from '@/api/applications'

const router = useRouter()

const followUps = ref<FollowUpList>({ count: 0, items: [] })

const loopSteps = [
  { step: '①', title: '自然语言对话建档', desc: '多轮对话 · 信息抽取 · 缺失信息追问' },
  { step: '②', title: 'AI 简历生成', desc: '结构化档案 · 版本管理 · PDF/Word 导出' },
  { step: '③', title: 'JD 岗位精准匹配', desc: '岗位解析 · 技能矩阵 · 可解释评分' },
  { step: '④', title: 'AI 反向优化', desc: '发现差距 · 追问补充 · 真实经历改写' },
  { step: '⑤', title: '投递管理与反馈', desc: '模拟投递 · 状态追踪 · 主动提醒' },
]

onMounted(async () => {
  try {
    followUps.value = (await listFollowUps()).data
  } catch {
    // 首页提醒失败不阻塞主流程
  }
})
</script>

<template>
  <div class="dashboard">
    <section class="hero">
      <h1>从简历生成到精准投递的<br />全流程智能求职系统</h1>
      <p>
        通过自然语言对话建立个人档案，AI 生成结构化简历，精准匹配岗位，<br />
        并在投递反馈中持续优化——形成完整求职闭环。
      </p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="router.push('/chat')">开始对话建档</el-button>
        <el-button size="large" class="hero-btn-plain" @click="router.push('/jobs')">浏览岗位</el-button>
      </div>
    </section>

    <el-card v-if="followUps.count > 0" shadow="never" class="reminder-card" data-testid="follow-up-reminder">
      <div class="reminder-row">
        <div>
          <h3>有 {{ followUps.count }} 条投递需要跟进</h3>
          <p class="reminder-desc">
            {{ followUps.items.slice(0, 3).map((item) => item.position).join('、') }}
            <template v-if="followUps.count > 3"> 等</template>
            · 超过 3 天未更新状态，建议主动联系 HR。
          </p>
        </div>
        <el-button type="primary" data-testid="goto-applications" @click="router.push('/applications')">
          去处理
        </el-button>
      </div>
    </el-card>

    <section class="loop">
      <h2>求职业务闭环</h2>
      <p class="loop-sub">反馈回流，驱动下一轮简历优化</p>
      <div class="loop-cards">
        <div v-for="s in loopSteps" :key="s.step" class="loop-card">
          <div class="step-badge">{{ s.step }}</div>
          <div class="step-title">{{ s.title }}</div>
          <div class="step-desc">{{ s.desc }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  background: linear-gradient(135deg, var(--cf-primary-dark) 0%, var(--cf-primary) 55%, var(--cf-primary-light) 100%);
  border-radius: 12px;
  padding: 56px 48px;
  color: #fff;
}

.hero h1 {
  font-size: 34px;
  line-height: 1.35;
  font-weight: 700;
}

.hero p {
  margin-top: 16px;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.85);
}

.hero-actions {
  margin-top: 28px;
  display: flex;
  gap: 12px;
}

.hero-btn-plain {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.4);
  color: #fff;
}

.hero-btn-plain:hover {
  background: rgba(255, 255, 255, 0.22);
  border-color: rgba(255, 255, 255, 0.6);
  color: #fff;
}

.reminder-card {
  margin-top: 20px;
  border-color: #fcd34d;
  background: #fffbeb;
}

.reminder-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.reminder-row h3 {
  font-size: 16px;
  color: #92400e;
}

.reminder-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #92400e;
}

.loop {
  margin-top: 36px;
}

.loop h2 {
  font-size: 22px;
}

.loop-sub {
  margin-top: 6px;
  color: var(--cf-text-secondary);
  font-size: 14px;
}

.loop-cards {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.loop-card {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  padding: 20px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.loop-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.1);
}

.step-badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #eff6ff;
  color: var(--cf-primary);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-title {
  margin-top: 14px;
  font-size: 15px;
  font-weight: 600;
}

.step-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--cf-text-secondary);
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .loop-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
