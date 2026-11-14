<script setup lang="ts">
import { ElMessage } from 'element-plus'

import { useResumeDraftStore } from '@/stores/resumeDraft'
import { buildDirectionTags } from '@/utils/resumeWizard'

/** 第 2 步：求职画像与方向。方向标签由已填字段确定性生成，不调用 AI。 */
const store = useResumeDraftStore()

const INDUSTRY_OPTIONS = [
  '互联网',
  '企业服务',
  '金融',
  '制造业',
  '教育',
  '医疗健康',
  '消费零售',
  '文化传媒',
  '其他',
]

function generateTags(): void {
  const tags = buildDirectionTags(store.content.direction, store.content.skills)
  if (tags.length === 0) {
    ElMessage.warning('请先填写行业、目标岗位或目标城市，再生成方向标签')
    return
  }
  store.content.direction.directionTags = tags
  ElMessage.success(`已生成 ${tags.length} 个求职方向标签`)
}

function removeTag(index: number): void {
  store.content.direction.directionTags.splice(index, 1)
}
</script>

<template>
  <section class="step-direction">
    <header class="step-head">
      <h2 class="step-head__title">画像与方向</h2>
      <p class="step-head__desc">明确目标行业与岗位，后续简历内容会围绕这个方向组织。</p>
    </header>

    <el-form label-position="top" @submit.prevent>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="目标行业">
            <el-select
              v-model="store.content.direction.industry"
              class="full-width"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入目标行业"
            >
              <el-option
                v-for="option in INDUSTRY_OPTIONS"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="目标岗位" required>
            <el-input
              v-model="store.content.direction.targetPosition"
              data-testid="direction-position"
              maxlength="60"
              placeholder="如 Java 后端开发工程师"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="目标城市">
            <el-input
              v-model="store.content.direction.targetCity"
              maxlength="30"
              placeholder="如 深圳"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="期望薪资">
            <el-input
              v-model="store.content.direction.expectedSalary"
              maxlength="40"
              placeholder="如 8-12K"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="求职目标">
        <el-input
          v-model="store.content.direction.jobGoal"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="用一两句话说明你想找什么样的岗位，以及希望在这个岗位上达成什么"
        />
      </el-form-item>
    </el-form>

    <section class="tag-section">
      <div class="tag-section__head">
        <div>
          <h3 class="tag-section__title">求职方向标签</h3>
          <p class="tag-section__desc">根据行业、岗位、城市和技能自动汇总，可删除不需要的标签。</p>
        </div>
        <el-button type="primary" plain data-testid="generate-direction-tags" @click="generateTags">
          生成求职方向标签
        </el-button>
      </div>

      <div v-if="store.content.direction.directionTags.length > 0" class="tag-list">
        <el-tag
          v-for="(tag, index) in store.content.direction.directionTags"
          :key="`${tag}-${index}`"
          closable
          effect="plain"
          @close="removeTag(index)"
        >
          {{ tag }}
        </el-tag>
      </div>
      <p v-else class="tag-empty">还没有方向标签，填写上方信息后点击「生成求职方向标签」。</p>
    </section>
  </section>
</template>

<style scoped>
.step-direction {
  max-width: 760px;
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

.full-width {
  width: 100%;
}

.tag-section {
  margin-top: var(--cf-space-2);
  padding-top: var(--cf-space-4);
  border-top: 1px solid var(--cf-border);
}

.tag-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cf-space-4);
  margin-bottom: var(--cf-space-3);
}

.tag-section__title {
  margin: 0;
  font-size: var(--cf-font-base);
  font-weight: 600;
  color: var(--cf-text);
}

.tag-section__desc {
  margin: var(--cf-space-1) 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
}

.tag-empty {
  margin: 0;
  font-size: var(--cf-font-sm);
  color: var(--cf-text-tertiary);
}

@media (max-width: 767px) {
  .tag-section__head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
