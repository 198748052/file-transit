<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 20 });

const ICONS: Record<string, string[]> = {
  cloud: [
    'M17.5 19a4.5 4.5 0 0 0 .5-8.97A6 6 0 0 0 6.2 8.4 3.8 3.8 0 0 0 6 19z',
  ],
  folder: ['M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'],
  file: ['M14 3H6v18h12V7z', 'M14 3v4h4'],
  upload: ['M12 16V4', 'M6 10l6-6 6 6', 'M4 20h16'],
  download: ['M12 4v12', 'M6 10l6 6 6-6', 'M4 20h16'],
  plus: ['M12 5v14', 'M5 12h14'],
  search: ['M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z', 'M20 20l-3.6-3.6'],
  grid: ['M4 4h7v7H4z', 'M13 4h7v7h-7z', 'M4 13h7v7H4z', 'M13 13h7v7h-7z'],
  list: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  more: ['M5 12h.01', 'M12 12h.01', 'M19 12h.01'],
  trash: ['M4 7h16', 'M9 7V5h6v2', 'M6 7l1 13h10l1-13', 'M10 11v6', 'M14 11v6'],
  link: [
    'M10 13a5 5 0 0 0 7.1 0l2-2a5 5 0 0 0-7.1-7.1l-1.2 1.2',
    'M14 11a5 5 0 0 0-7.1 0l-2 2A5 5 0 0 0 12 20.1l1.2-1.2',
  ],
  qr: ['M4 4h6v6H4z', 'M14 4h6v6h-6z', 'M4 14h6v6H4z', 'M14 14h3v3h-3z', 'M20 14v2', 'M14 20h2'],
  edit: ['M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17z', 'M14.5 6.5l3 3'],
  move: ['M4 12h15', 'M13 6l6 6-6 6'],
  chevron: ['M9 6l6 6-6 6'],
  home: ['M3 11l9-8 9 8', 'M5 10v10h14V10'],
  close: ['M6 6l12 12', 'M18 6L6 18'],
  refresh: ['M20 12a8 8 0 1 1-2.4-5.7', 'M20 4v5h-5'],
  check: ['M5 13l4 4L19 7'],
  clock: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M12 7v5l3 2'],
  share: ['M8 12a3 3 0 1 0 0 6 3 3 0 0 0 0-6z', 'M16 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6z', 'M16 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6z', 'M10.5 13.5l3.5-2', 'M10.5 16.5l3.5 2'],
  storage: ['M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3z', 'M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6', 'M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3'],
  pack: ['M4 4h16v16H4z', 'M4 10h16', 'M10 4v6'],
};

const paths = computed(() => ICONS[props.name] ?? ICONS.file!);
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>
