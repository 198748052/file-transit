<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import {
  closeCopyDialog,
  confirmState,
  copyDialog,
  dismissToast,
  settleConfirm,
  toasts,
} from '../utils/feedback';
import { copyText } from '../utils/clipboard';

const confirmButton = ref<HTMLButtonElement | null>(null);
const cancelButton = ref<HTMLButtonElement | null>(null);
const copyInput = ref<HTMLInputElement | null>(null);
const copied = ref(false);

watch(
  () => confirmState.open,
  async (open) => {
    if (!open) return;
    await nextTick();
    (confirmState.danger ? cancelButton : confirmButton).value?.focus();
  },
);

watch(
  () => copyDialog.open,
  async (open) => {
    copied.value = false;
    if (!open) return;
    await nextTick();
    copyInput.value?.focus();
    copyInput.value?.select();
  },
);

function selectCopyText(event: FocusEvent) {
  (event.target as HTMLInputElement).select();
}

async function copyFromDialog() {
  if (await copyText(copyDialog.text)) {
    copied.value = true;
    window.setTimeout(() => (copied.value = false), 1500);
  }
}
</script>

<template>
  <div class="toast-stack" aria-live="polite" aria-atomic="false">
    <button
      v-for="item in toasts"
      :key="item.id"
      type="button"
      class="toast-item"
      :class="item.tone"
      @click="dismissToast(item.id)"
    >
      {{ item.message }}
    </button>
  </div>

  <div
    v-if="confirmState.open"
    class="modal-mask"
    @click.self="settleConfirm(false)"
    @keydown.esc="settleConfirm(false)"
  >
    <div
      class="card modal confirm-modal"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirm-title"
      aria-describedby="confirm-message"
    >
      <h2 id="confirm-title">{{ confirmState.title }}</h2>
      <p id="confirm-message" class="confirm-message">{{ confirmState.message }}</p>
      <div class="row mt-lg">
        <button ref="cancelButton" class="btn btn-ghost" type="button" @click="settleConfirm(false)">
          {{ confirmState.cancelText }}
        </button>
        <button
          ref="confirmButton"
          class="btn"
          :class="confirmState.danger ? 'btn-danger' : 'btn-primary'"
          type="button"
          @click="settleConfirm(true)"
        >
          {{ confirmState.confirmText }}
        </button>
      </div>
    </div>
  </div>

  <div v-if="copyDialog.open" class="modal-mask" @click.self="closeCopyDialog" @keydown.esc="closeCopyDialog">
    <div class="card modal" role="dialog" aria-modal="true" aria-label="复制链接">
      <h2>{{ copyDialog.title }}</h2>
      <p class="muted text-sm">自动复制未成功，请手动选择并复制下面的链接。</p>
      <input
        ref="copyInput"
        class="copy-input"
        type="text"
        :value="copyDialog.text"
        readonly
        spellcheck="false"
        @focus="selectCopyText"
      />
      <div class="row mt-lg">
        <button class="btn btn-ghost" type="button" @click="closeCopyDialog">关闭</button>
        <button class="btn btn-primary" type="button" @click="copyFromDialog">
          {{ copied ? '已复制' : '复制' }}
        </button>
      </div>
    </div>
  </div>
</template>
