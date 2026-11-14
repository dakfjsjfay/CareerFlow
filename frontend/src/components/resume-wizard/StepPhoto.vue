<script setup lang="ts">
import { useResumeDraftStore } from '@/stores/resumeDraft'

import PhotoUploader from './PhotoUploader.vue'

/** 第 6 步：证件照。裁剪结果直接写入草稿 photo，供支持头像的模板渲染。 */
const store = useResumeDraftStore()

function handleChange(payload: { dataUrl: string; width: number; height: number }): void {
  store.content.photo.dataUrl = payload.dataUrl
  store.content.photo.width = payload.width
  store.content.photo.height = payload.height
}

function handleRemove(): void {
  store.content.photo.dataUrl = null
  store.content.photo.width = null
  store.content.photo.height = null
  store.content.photo.assetId = null
}
</script>

<template>
  <section class="step-photo">
    <header class="step-head">
      <h2 class="step-head__title">证件照</h2>
      <p class="step-head__desc">
        上传一张清晰的证件照，部分模板会在简历右上角展示；不上传也不影响生成。
      </p>
    </header>

    <div class="photo-card">
      <PhotoUploader
        :data-url="store.content.photo.dataUrl"
        @change="handleChange"
        @remove="handleRemove"
      />
    </div>

    <section class="spec-card">
      <h3 class="spec-card__title">格式与尺寸规范</h3>
      <ul class="spec-list">
        <li>支持 JPG、PNG 格式，建议原图清晰、光线均匀。</li>
        <li>裁剪输出固定为 295 × 413 像素（一寸照比例），保存为 JPEG。</li>
        <li>建议使用纯色背景、正面免冠照片，避免过度美颜或遮挡面部。</li>
        <li>照片仅在本地裁剪，不会上传原始图片。</li>
      </ul>
      <p v-if="store.content.photo.dataUrl" class="spec-current">
        当前照片尺寸：{{ store.content.photo.width ?? 295 }} ×
        {{ store.content.photo.height ?? 413 }} 像素
      </p>
      <p v-else class="spec-current">当前还没有上传照片。</p>
    </section>
  </section>
</template>

<style scoped>
.step-photo {
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

.photo-card {
  padding: var(--cf-space-4);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-card-bg);
}

.spec-card {
  margin-top: var(--cf-space-4);
  padding: var(--cf-space-4);
  border: 1px solid var(--cf-border);
  border-radius: var(--cf-radius-md);
  background: var(--cf-bg);
}

.spec-card__title {
  margin: 0 0 var(--cf-space-2);
  font-size: var(--cf-font-base);
  font-weight: 600;
  color: var(--cf-text);
}

.spec-list {
  margin: 0;
  padding-left: 18px;
  font-size: var(--cf-font-sm);
  line-height: 1.8;
  color: var(--cf-text-secondary);
}

.spec-current {
  margin: var(--cf-space-2) 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
}
</style>
