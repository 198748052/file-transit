export function formatBytes(bytes: number): string {
  if (!bytes || bytes < 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** i;
  return `${value.toFixed(value >= 100 || i === 0 ? 0 : 1)} ${units[i]}`;
}

export function formatDateTime(ms: number | null): string {
  if (ms === null) return '—';
  return new Date(ms).toLocaleString('zh-CN', { hour12: false });
}

/** "永久" / "3 天后过期" / "已于 x 过期" */
export function describeExpiry(expiresAt: number | null): string {
  if (expiresAt === null) return '永久';
  const diff = expiresAt - Date.now();
  const day = 86_400_000;
  if (diff <= 0) return '已过期';
  if (diff < day) {
    const hours = Math.ceil(diff / 3_600_000);
    return `${hours} 小时后过期`;
  }
  return `${Math.ceil(diff / day)} 天后过期`;
}

export function truncate(name: string, len = 40): string {
  return name.length > len ? name.slice(0, len - 1) + '…' : name;
}
