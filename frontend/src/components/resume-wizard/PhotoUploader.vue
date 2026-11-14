<script setup lang="ts">
import { ref, watch } from 'vue'

/**
 * 证件照上传与裁剪：画布固定 295×413，等比覆盖 + 缩放/位置滑杆，导出 JPEG。
 * 不依赖额外裁剪库，全部在本地完成，不上传原始图片。
 */
const props = withDefaults(
  defineProps<{
    dataUrl?: string | null
  }>(),
  { dataUrl: null },
)

const emit = defineEmits<{
  change: [payload: { dataUrl: string; width: number; height: number }]
  remove: []
}>()

const CROP_WIDTH = 295
const CROP_HEIGHT = 413

const fileInput = ref<HTMLInputElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const image = ref<HTMLImageElement | null>(null)
const fileName = ref('')
const zoom = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const error = ref('')

function pickFile(): void {
  fileInput.value?.click()
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (!file.type.startsWith('image/')) {
    error.value = '请选择 JPG 或 PNG 格式的图片'
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    loadImage(String(reader.result), file.name)
  }
  reader.onerror = () => {
    error.value = '图片读取失败，请更换一张图片重试'
  }
  reader.readAsDataURL(file)
}

function loadImage(src: string, name: string): void {
  const next = new Image()
  next.onload = () => {
    error.value = ''
    image.value = next
    fileName.value = name
    zoom.value = 1
    offsetX.value = 0
    offsetY.value = 0
    draw()
  }
  next.onerror = () => {
    error.value = '图片加载失败，请更换一张图片重试'
  }
  next.src = src
}

function draw(): void {
  const canvas = canvasRef.value
  const source = image.value
  if (!canvas || !source) {
    return
  }
  const context = canvas.getContext('2d')
  if (!context) {
    return
  }
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, CROP_WIDTH, CROP_HEIGHT)

  const cover = Math.max(CROP_WIDTH / source.width, CROP_HEIGHT / source.height) * zoom.value
  const drawWidth = source.width * cover
  const drawHeight = source.height * cover
  const maxShiftX = Math.max(0, (drawWidth - CROP_WIDTH) / 2)
  const maxShiftY = Math.max(0, (drawHeight - CROP_HEIGHT) / 2)
  const x = (CROP_WIDTH - drawWidth) / 2 + (offsetX.value / 100) * maxShiftX
  const y = (CROP_HEIGHT - drawHeight) / 2 + (offsetY.value / 100) * maxShiftY

  context.drawImage(source, x, y, drawWidth, drawHeight)
}

function apply(): void {
  const canvas = canvasRef.value
  if (!canvas) {
    return
  }
  draw()
  const dataUrl = canvas.toDataURL('image/jpeg', 0.92)
  emit('change', { dataUrl, width: CROP_WIDTH, height: CROP_HEIGHT })
  image.value = null
  fileName.value = ''
}

function remove(): void {
  emit('remove')
}

watch([zoom, offsetX, offsetY], draw)
</script>

<template>
  <div class="photo-uploader">
    <div class="photo-frame">
      <canvas
        ref="canvasRef"
        v-show="image"
        class="photo-canvas"
        :width="CROP_WIDTH"
        :height="CROP_HEIGHT"
      />
      <img
        v-if="!image && props.dataUrl"
        :src="props.dataUrl"
        alt="当前证件照"
        class="photo-image"
      />
      <div v-else-if="!image" class="photo-empty">
        <span class="photo-empty__icon">＋</span>
        <span>还没有上传证件照</span>
        <span class="photo-empty__hint">支持 JPG / PNG</span>
      </div>
    </div>

    <div class="photo-controls">
      <input
        ref="fileInput"
        class="photo-file"
        type="file"
        accept="image/*"
        @change="onFileChange"
      />

      <div class="photo-actions">
        <el-button size="small" @click="pickFile">
          {{ image ? '重新选择' : '上传照片' }}
        </el-button>
        <el-button v-if="image" size="small" type="primary" @click="apply">使用这张照片</el-button>
        <el-button v-if="!image && props.dataUrl" size="small" @click="remove">移除照片</el-button>
      </div>

      <div v-if="image" class="photo-sliders">
        <p class="photo-file-name">当前图片：{{ fileName }}</p>
        <div class="photo-slider">
          <span class="photo-slider__label">缩放</span>
          <el-slider v-model="zoom" :min="1" :max="3" :step="0.05" :show-tooltip="false" />
        </div>
        <div class="photo-slider">
          <span class="photo-slider__label">水平位置</span>
          <el-slider v-model="offsetX" :min="-100" :max="100" :show-tooltip="false" />
        </div>
        <div class="photo-slider">
          <span class="photo-slider__label">垂直位置</span>
          <el-slider v-model="offsetY" :min="-100" :max="100" :show-tooltip="false" />
        </div>
      </div>

      <p v-if="error" class="photo-error">{{ error }}</p>
      <p class="photo-tip">建议使用正面免冠、背景简洁的照片；裁剪结果会保存为 295×413 像素的 JPEG。</p>
    </div>
  </div>
</template>

<style scoped>
.photo-uploader {
  display: flex;
  align-items: flex-start;
  gap: var(--cf-space-5);
}

.photo-frame {
  width: 160px;
  height: 224px;
  flex-shrink: 0;
  border: 1px dashed var(--cf-border-strong);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-bg);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.photo-canvas,
.photo-image {
  width: 160px;
  height: 224px;
  display: block;
  object-fit: cover;
}

.photo-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--cf-space-1);
  padding: var(--cf-space-3);
  text-align: center;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.photo-empty__icon {
  font-size: 22px;
  color: var(--cf-primary);
}

.photo-empty__hint {
  color: var(--cf-text-tertiary);
}

.photo-controls {
  flex: 1;
  min-width: 0;
}

.photo-file {
  display: none;
}

.photo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cf-space-2);
}

.photo-sliders {
  margin-top: var(--cf-space-3);
}

.photo-file-name {
  margin: 0 0 var(--cf-space-1);
  font-size: var(--cf-font-xs);
  color: var(--cf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.photo-slider {
  display: flex;
  align-items: center;
  gap: var(--cf-space-3);
}

.photo-slider__label {
  width: 64px;
  flex-shrink: 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-text-secondary);
}

.photo-slider :deep(.el-slider) {
  flex: 1;
}

.photo-error {
  margin: var(--cf-space-2) 0 0;
  font-size: var(--cf-font-xs);
  color: var(--cf-danger);
}

.photo-tip {
  margin: var(--cf-space-2) 0 0;
  font-size: var(--cf-font-xs);
  line-height: 1.6;
  color: var(--cf-text-tertiary);
}

@media (max-width: 767px) {
  .photo-uploader {
    flex-direction: column;
  }
}
</style>
