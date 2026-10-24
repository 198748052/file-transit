<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import QRCode from 'qrcode';
import { toast } from '../utils/feedback';

const props = defineProps<{ title: string; url: string }>();
const emit = defineEmits<{ (e: 'close'): void }>();

const dataUrl = ref('');
const error = ref('');
const copied = ref(false);

async function render() {
  error.value = '';
  try {
    dataUrl.value = await QRCode.toDataURL(props.url, { width: 280, margin: 1, errorCorrectionLevel: 'M' });
  } catch {
    error.value = '二维码生成失败';
  }
}

onMounted(render);
watch(() => props.url, render);

async function copy() {
  try {
    await navigator.clipboard.writeText(props.url);
    copied.value = true;
    setTimeout(() => (copied.value = false), 1500);
  } catch {
    toast('复制失败，请手动选择上方链接。', 'error');
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="card modal text-center" role="dialog" aria-modal="true" aria-label="分享二维码">
      <h2>{{ title }}</h2>
      <img v-if="dataUrl" :src="dataUrl" alt="分享二维码" class="qr-img" />
      <p v-else-if="error" class="alert error">{{ error }}</p>
      <p v-else class="muted">生成中…</p>
      <p class="muted qr-url">{{ url }}</p>
      <div class="row mt-lg">
        <button class="btn btn-ghost" @click="copy">{{ copied ? '已复制' : '复制链接' }}</button>
        <button class="btn btn-primary" @click="emit('close')">关闭</button>
      </div>
    </div>
  </div>
</template>
