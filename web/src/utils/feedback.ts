import { reactive } from 'vue';

export type ToastTone = 'info' | 'success' | 'error';

export interface ToastItem {
  id: number;
  message: string;
  tone: ToastTone;
}

export const toasts = reactive<ToastItem[]>([]);
const toastTimers = new Map<number, number>();
let toastSeq = 0;

export function toast(message: string, tone: ToastTone = 'info'): number {
  const id = ++toastSeq;
  toasts.push({ id, message, tone });
  const timer = window.setTimeout(() => dismissToast(id), tone === 'error' ? 4200 : 2800);
  toastTimers.set(id, timer);
  return id;
}

export function dismissToast(id: number) {
  const index = toasts.findIndex((item) => item.id === id);
  if (index >= 0) toasts.splice(index, 1);
  const timer = toastTimers.get(id);
  if (timer !== undefined) {
    window.clearTimeout(timer);
    toastTimers.delete(id);
  }
}

export interface ConfirmOptions {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
}

interface ConfirmState {
  open: boolean;
  title: string;
  message: string;
  confirmText: string;
  cancelText: string;
  danger: boolean;
  resolve: ((ok: boolean) => void) | null;
}

export const confirmState = reactive<ConfirmState>({
  open: false,
  title: '确认操作',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: true,
  resolve: null,
});

export function confirmAction(options: ConfirmOptions): Promise<boolean> {
  if (confirmState.resolve) confirmState.resolve(false);
  confirmState.title = options.title ?? '确认操作';
  confirmState.message = options.message;
  confirmState.confirmText = options.confirmText ?? '确定';
  confirmState.cancelText = options.cancelText ?? '取消';
  confirmState.danger = options.danger ?? true;
  confirmState.open = true;
  return new Promise<boolean>((resolve) => {
    confirmState.resolve = resolve;
  });
}

export function settleConfirm(ok: boolean) {
  if (!confirmState.open) return;
  const resolve = confirmState.resolve;
  confirmState.open = false;
  confirmState.resolve = null;
  resolve?.(ok);
}

export const copyDialog = reactive({ open: false, title: '复制链接', text: '' });

export function showCopyDialog(text: string, title = '复制链接') {
  copyDialog.title = title;
  copyDialog.text = text;
  copyDialog.open = true;
}

export function closeCopyDialog() {
  copyDialog.open = false;
}
