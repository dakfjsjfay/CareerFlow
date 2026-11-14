<script setup lang="ts">
import { ref } from 'vue'

import { useResumeDraftStore } from '@/stores/resumeDraft'

/** 第 5 步：软性信息（自我评价、个人优势、表达建议）。 */
const store = useResumeDraftStore()

const EXAMPLES = [
  '性格踏实、执行力强，习惯把复杂任务拆解成可执行的小步骤并按时交付。',
  '具备良好的沟通与协作意识，能在跨团队合作中主动同步进展、推动问题闭环。',
  '学习能力强，持续关注行业新技术，并愿意把新方法应用到实际工作中。',
]

const newStrength = ref('')

function applyExample(text: string): void {
  store.content.soft.selfEvaluation = text
}

function addStrength(): void {
  const value = newStrength.value.trim()
  if (!value) {
    return
  }
  if (!store.content.soft.strengths.includes(value)) {
    store.content.soft.strengths.push(value)
  }
  newStrength.value = ''
}

function removeStrength(index: number): void {
  store.content.soft.strengths.splice(index, 1)
}
</script>

<template>
  <section class="step-soft">
    <header class="step-head">
      <h2 class="step-head__title">软性信息</h2>
      <p class="step-head__desc">用简洁的语言补充你的个人特质，避免空泛的形容词堆砌。</p>
    </header>

    <section class="panel">
      <h3 class="panel__title">自我评价</h3>
      <el-input
        v-model="store.content.soft.selfEvaluation"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="用 3-5 句话概括你的优势、做事风格和求职动机"
      />
      <div class="examples">
        <p class="examples__label">参考示例（点击一键填入）：</p>
        <div class="examples__list">
          <button
            v-for="(example, index) in EXAMPLES"
            :key="example"
            type="button"
            class="example"
            @click="applyExample(example)"
          >
            <span class="example__index">示例 {{ index + 1 }}</span>
            <span class="example__text">{{ example }}</span>
          </button>
        </div>
      </div>
    </section>

    <section class="panel">
      <h3 class="panel__title">个人优势</h3>
      <div class="strength-input">
        <el-input
          v-model="newStrength"
          maxlength="40"
          placeholder="输入一条个人优势后按回车或点击添加"
          @keyup.enter="addStrength"
        />
        <el-button type="primary" plain @click="addStrength">添加</el-button>
      </div>

      <div v-if="store.content.soft.strengths.length > 0" class="strength-list">
        <el-tag
          v-for="(strength, index) in store.content.soft.strengths"
          :key="`${strength}-${index}`"
          closable
          effect="plain"
          @close="removeStrength(index)"
        >
          {{ strength }}
        </el-tag>
      </div>
      <p v-else class="hint">还没有个人优势，建议补充 3-5 条与目标岗位相关的能力。</p>
    </section>

    <section class="panel">
      <h3 class="panel__title">表达建议</h3>
      <ul v-if="store.content.soft.suggestions.length > 0" class="suggestion-list">
        <li v-for="(suggestion, index) in store.content.soft.suggestions" :key="index">
          {{ suggestion }}
        </li>
      </ul>
      <p v-else class="hint">暂无表达建议，完成 STAR 结构化后系统会给出针对性的优化提示。</p>
    </section>
  </section>
</template>

<style scoped>
.step-soft {
  max-width: 820px;
}

.step-head {
  margin-bottom: var(--cf-space-4);
}

.step-head__title {
  margin: 0;
  font-size: var(--cf-font-lg);
  font-weight: 600;
  color: var(--cf-text);
}

.step-head__desc {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-secondary);
}

.panel {
  padding: var(--cf-space-4);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
}

.panel + .panel {
  margin-top: var(--cf-space-4);
}

.panel__title {
  margin: 0 0 var(--cf-space-3);
  font-size: var(--cf-font-base);
  font-weight: 600;
  color: var(--cf-text);
}

.examples {
  margin-top: var(--cf-space-3);
}

.examples__label {
  margin: 0 0 var(--cf-space-2);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.examples__list {
  display: flex;
  flex-direction: column;
  gap: var(--cf-space-2);
}

.example {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--cf-space-2) var(--cf-space-3);
  border: 1px dashed var(--cf-border-strong);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-bg);
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.example:hover {
  border-color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.example__index {
  font-size: var(--cf-font-xs);
  color: var(--cf-primary);
}

.example__text {
  font-size: var(--cf-font-sm);
  line-height: 1.6;
  color: var(--cf-text-secondary);
}

.strength-input {
  display: flex;
  gap: var(--cf-space-2);
}

.strength-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
  margin-top: var(--cf-space-3);
}

.hint {
  margin: var(--cf-space-2) 0 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-tertiary);
}

.suggestion-list {
  margin: 0;
  padding-left: 18px;
  font-size: var(--cf-font-sm);
  line-height: 1.8;
  color: var(--cf-text-secondary);
}
</style>
