export interface ActionMeta {
  label: string;
  tone: string;
}

const ACTIONS: Record<string, ActionMeta> = {
  'auth.login': { label: '登录成功', tone: 'auth' },
  'auth.login_failed': { label: '登录失败', tone: 'danger' },
  'auth.login_blocked': { label: '登录被限流', tone: 'danger' },
  'auth.password_change': { label: '修改密码', tone: 'auth' },
  'auth.password_reset': { label: '恢复 .env 密码', tone: 'auth' },
  'auth.username_change': { label: '修改用户名', tone: 'auth' },
  'auth.username_reset': { label: '恢复 .env 用户名', tone: 'auth' },

  'file.upload': { label: '上传文件', tone: 'ok' },
  'file.upload_abort': { label: '取消上传', tone: 'warn' },
  'file.rename': { label: '重命名文件', tone: 'info' },
  'file.update': { label: '修改文件设置', tone: 'info' },
  'file.move': { label: '移动文件', tone: 'info' },
  'file.download': { label: '下载文件', tone: 'ok' },
  'file.delete': { label: '删除文件', tone: 'danger' },
  'file.dedup': { label: '重复文件秒传', tone: 'ok' },

  'folder.create': { label: '新建文件夹', tone: 'ok' },
  'folder.rename': { label: '重命名文件夹', tone: 'info' },
  'folder.move': { label: '移动文件夹', tone: 'info' },
  'folder.delete': { label: '删除文件夹', tone: 'danger' },

  'share.view': { label: '分享页访问', tone: 'muted' },
  'share.download': { label: '分享下载', tone: 'ok' },
  'share.preview': { label: '分享预览', tone: 'info' },
  'share.rate_limited': { label: '分享限流', tone: 'warn' },

  'collection.create': { label: '创建集合', tone: 'ok' },
  'collection.update': { label: '编辑集合', tone: 'info' },
  'collection.delete': { label: '删除集合', tone: 'danger' },
  'collection.download': { label: '集合下载', tone: 'ok' },
  'collection.view': { label: '集合页访问', tone: 'muted' },
  'collection.preview': { label: '集合预览', tone: 'info' },
  'collection.download_all': { label: '集合一键下载', tone: 'ok' },

  'folder_share.create': { label: '创建文件夹分享', tone: 'ok' },
  'folder_share.update': { label: '编辑文件夹分享', tone: 'info' },
  'folder_share.delete': { label: '删除文件夹分享', tone: 'danger' },
  'folder_share.view': { label: '文件夹分享页访问', tone: 'muted' },
  'folder_share.preview': { label: '文件夹分享预览', tone: 'info' },
  'folder_share.download': { label: '文件夹分享下载', tone: 'ok' },
  'folder_share.download_all': { label: '文件夹一键下载', tone: 'ok' },

  'text.create': { label: '保存文本', tone: 'ok' },
  'text.update': { label: '编辑文本', tone: 'info' },
  'text.delete': { label: '删除文本', tone: 'danger' },
  'text.view': { label: '文本页访问', tone: 'muted' },

  'settings.r2_update': { label: '更新存储配置', tone: 'info' },
  'settings.r2_test': { label: '测试存储连接', tone: 'info' },
  'settings.r2_cors': { label: '应用 CORS', tone: 'info' },
  'settings.site_update': { label: '更新站点地址', tone: 'info' },
  'settings.r2_usage_token': { label: '更新账户用量 Token', tone: 'auth' },
  'settings.share_limit_update': { label: '更新分享限制', tone: 'info' },

  'bucket.scan': { label: '扫描存储桶', tone: 'info' },
  'bucket.delete_orphans': { label: '清理残留对象', tone: 'danger' },
  'bucket.migrate_keys': { label: '迁移对象路径', tone: 'warn' },

  'system.update': { label: '远程更新', tone: 'warn' },
  'maintenance.expire': { label: '清理过期文件', tone: 'muted' },
  'maintenance.expire_texts': { label: '清理过期文本', tone: 'muted' },
  'maintenance.stale_upload': { label: '清理中断上传', tone: 'muted' },
  'logs.clear': { label: '清空操作记录', tone: 'danger' },
};

export function actionMeta(action: string): ActionMeta {
  return ACTIONS[action] ?? { label: action, tone: 'muted' };
}

/** A short human-readable view of the log entry's detail payload. */
export function describeDetail(log: { action: string; detail: unknown; targetName: string | null }): string {
  const detail = (log.detail ?? {}) as Record<string, unknown>;
  const parts: string[] = [];
  const num = (key: string, label: string) => {
    const value = detail[key];
    if (typeof value === 'number') parts.push(`${label} ${value}`);
  };
  const str = (key: string, label: string) => {
    const value = detail[key];
    if (typeof value === 'string' && value) parts.push(`${label} ${value}`);
  };

  switch (log.action) {
    case 'file.upload':
      num('size', '大小');
      break;
    case 'file.delete':
      num('size', '大小');
      break;
    case 'file.download':
      num('size', '大小');
      break;
    case 'file.move':
      num('moved', '数量');
      parts.push(detail.folderName ? `目标「${detail.folderName}」` : '目标 全部文件');
      break;
    case 'file.rename':
      str('previousName', '原名称');
      break;
    case 'file.update':
      if (Array.isArray(detail.changed) && detail.changed.length) parts.push(`改动 ${detail.changed.join(', ')}`);
      break;
    case 'folder.delete':
      num('deletedFolders', '文件夹');
      num('deletedFiles', '文件');
      break;
    case 'collection.create':
      num('fileCount', '文件');
      break;
    case 'collection.download_all':
      num('count', '文件');
      break;
    case 'folder_share.create':
      str('folderPath', '文件夹');
      break;
    case 'folder_share.download_all':
      num('count', '文件');
      break;
    case 'file.dedup':
      num('size', '大小');
      break;
    case 'text.create':
    case 'text.delete':
    case 'text.view':
      num('charCount', '字数');
      break;
    case 'text.update':
      if (Array.isArray(detail.changed) && detail.changed.length) parts.push(`改动 ${detail.changed.join(', ')}`);
      num('charCount', '字数');
      break;
    case 'share.rate_limited':
      str('reason', '原因');
      num('retryAfterSec', '等待秒数');
      break;
    case 'share.view':
    case 'collection.view':
    case 'folder_share.view':
      str('shareCode', '分享码');
      break;
    case 'auth.username_change':
      str('next', '新用户名');
      break;
    case 'settings.r2_update':
      str('bucket', '桶');
      break;
    case 'settings.site_update':
      str('baseUrl', '地址');
      break;
    case 'bucket.scan':
      num('objectCount', '对象');
      num('orphanCount', '残留');
      num('missingCount', '缺失');
      break;
    case 'bucket.delete_orphans':
      num('deleted', '删除');
      num('failed', '失败');
      break;
    case 'bucket.migrate_keys':
      num('migrated', '迁移');
      num('skipped', '跳过');
      num('remaining', '剩余');
      break;
    case 'maintenance.expire':
      num('count', '数量');
      break;
    case 'maintenance.stale_upload':
      num('count', '数量');
      break;
    case 'logs.clear':
      num('cleared', '条');
      break;
  }

  if (typeof detail.reason === 'string' && detail.reason) parts.push(`原因 ${detail.reason}`);
  return parts.join(' · ');
}
