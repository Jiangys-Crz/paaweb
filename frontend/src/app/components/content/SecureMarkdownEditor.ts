"use client";

import { config, XSSPlugin } from 'md-editor-rt';
import katex, { type KatexOptions } from 'katex';

export const editorXssOptions: Parameters<typeof XSSPlugin>[1] = {
  xss: (library: Parameters<NonNullable<Parameters<typeof XSSPlugin>[1]['xss']>>[0]) => ({
    whiteList: library.getDefaultWhiteList(),
    stripIgnoreTagBody: ['script', 'style', 'svg', 'math', 'iframe', 'object', 'embed', 'form'],
  }),
};

export const editorKatexConfig = (options: KatexOptions): KatexOptions => ({ ...options, trust: false });

// The editor has its own preview renderer. Sanitize raw author HTML before its
// trusted math/highlight plugins run, rather than leaving the default identity sanitizer.
config({
  // Supplying an instance skips the editor's CDN script and stylesheet loader;
  // RootLayout already includes the matching local KaTeX stylesheet.
  editorExtensions: { katex: { instance: katex } },
  katexConfig: editorKatexConfig,
  markdownItConfig: markdown => {
    markdown.use(XSSPlugin, editorXssOptions);
  },
});

export { MdEditor, type ExposeParam } from 'md-editor-rt';
