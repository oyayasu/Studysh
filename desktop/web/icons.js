// Small, self-contained outline icon set adapted from Lucide / Feather.
// See THIRD_PARTY_NOTICES.md (ISC / MIT).
// SVG avoids dependence on font glyph coverage and Windows WebView fonts.
const shapes = {
  house: '<path d="m3 10 9-7 9 7"/><path d="M5 9v12h14V9"/><path d="M9 21v-7h6v7"/>',
  pencil: '<path d="m16 5 3 3"/><path d="M4 20l4.4-1 11.1-11.1a2.1 2.1 0 0 0-3-3L5.4 16z"/>',
  'book-open': '<path d="M12 7c-2.8-2-6-2.3-9-1.5V20c3-1 6.5-.8 9 1 2.5-1.8 6-2 9-1V5.5C18 4.7 14.8 5 12 7Z"/><path d="M12 7v14"/>',
  brain: '<path d="M12 18V5a3 3 0 0 0-5.7-1.2A4 4 0 0 0 4 11a4 4 0 0 0 2 7.2A3 3 0 0 0 12 18Z"/><path d="M12 18V5a3 3 0 0 1 5.7-1.2A4 4 0 0 1 20 11a4 4 0 0 1-2 7.2A3 3 0 0 1 12 18Z"/><path d="M8 9c1 0 2 1 2 2M16 9c-1 0-2 1-2 2"/>',
  'file-down': '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M12 12v7m-3-3 3 3 3-3"/>',
  'file-up': '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M12 19v-7m-3 3 3-3 3 3"/>',
  settings: '<path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42M17.65 6.35l1.42-1.42"/><circle cx="12" cy="12" r="4"/>',
  'volume-2': '<path d="M11 5 6 9H3v6h3l5 4z"/><path d="M15.5 8.5a5 5 0 0 1 0 7m3-10a9 9 0 0 1 0 13"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  search: '<circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/>',
  'chevron-right': '<path d="m9 18 6-6-6-6"/>',
  'arrow-left': '<path d="m12 19-7-7 7-7M5 12h14"/>',
  'arrow-right': '<path d="m12 5 7 7-7 7M19 12H5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  download: '<path d="M12 3v12m-4-4 4 4 4-4M4 17v3h16v-3"/>',
  headphones: '<path d="M4 14v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="13" width="4" height="8" rx="2"/><rect x="17" y="13" width="4" height="8" rx="2"/>',
  check: '<path d="m5 12 5 5L20 7"/>',
  'cloud-download': '<path d="M7 18H5a4 4 0 0 1-.8-7.9A7.5 7.5 0 0 1 18.5 9 4.5 4.5 0 0 1 19 18h-2M12 12v9m-4-4 4 4 4-4"/>',
  'sparkles': '<path d="m12 3 1.5 5 5 1.5-5 1.5L12 16l-1.5-5L5.5 9.5l5-1.5zM19 16l.7 1.8L21.5 18l-1.8.7L19 21l-.7-2.3L16.5 18l1.8-.2z"/>'
};
export const iconNames = Object.freeze(Object.keys(shapes));
export function icon(name, size = 19) {
  const shape = shapes[name];
  if (!shape) throw new Error("Unknown icon: " + name);
  const pixels = Math.max(12, Math.min(48, Number(size) || 19));
  return '<svg class="ui-icon" aria-hidden="true" focusable="false" width="' + pixels + '" height="' + pixels +
    '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + shape + '</svg>';
}
export function mountStaticIcons(root = document) {
  root.querySelectorAll("[data-icon]").forEach(node => {
    if (node.dataset.iconMounted) return;
    node.innerHTML = icon(node.dataset.icon, node.classList.contains("nav-icon") ? 21 : 20);
    node.dataset.iconMounted = "true";
  });
}
