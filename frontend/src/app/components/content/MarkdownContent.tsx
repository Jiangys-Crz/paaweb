"use client";

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeRaw from 'rehype-raw';
import rehypeKatex from 'rehype-katex';
import rehypeSanitize, { defaultSchema, type Options } from 'rehype-sanitize';

interface MarkdownContentProps {
  content?: string;
}

const normalizeMathBlocks = (value: string) => {
  return value.replace(
    /^(\s*)\$\$([^\n]+?)\$\$(\s*)$/gm,
    (_match, indent: string, expression: string) => `${indent}$$\n${expression.trim()}\n$$`
  );
};

// Sanitize author HTML before KaTeX creates its trusted MathML and layout styles.
// Only fixed math classes are admitted, never author styles/events/SVG.
export const markdownSchema: Options = {
  ...defaultSchema,
  tagNames: defaultSchema.tagNames?.filter(tag => !['input', 'form', 'button', 'select', 'textarea'].includes(tag)),
  attributes: {
    ...defaultSchema.attributes,
    code: [...(defaultSchema.attributes?.code || []), ['className', /^language-./, 'math-inline', 'math-display']],
  },
  protocols: { ...defaultSchema.protocols, href: ['http', 'https', 'mailto'], src: ['http', 'https'] },
  strip: [...(defaultSchema.strip || []), 'script', 'style', 'iframe', 'object', 'embed', 'form', 'svg', 'math'],
};

const MarkdownContent = ({ content = '' }: MarkdownContentProps) => {
  if (!content.trim()) {
    return <div className="bits-empty">暂无内容</div>;
  }

  const normalizedContent = normalizeMathBlocks(content);

  return (
    <article className="markdown-content">
      <ReactMarkdown 
        remarkPlugins={[remarkGfm, remarkMath]} 
        rehypePlugins={[rehypeRaw, [rehypeSanitize, markdownSchema], [rehypeKatex, { trust: false }]]}
      >
        {normalizedContent}
      </ReactMarkdown>
    </article>
  );
};

export default MarkdownContent;
