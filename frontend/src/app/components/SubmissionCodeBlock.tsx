'use client';

import { useEffect, useState } from 'react';
import { Button, message } from 'antd';
import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import 'highlight.js/styles/github.css';
import { copySubmissionCode, highlightSubmissionCode } from '@/app/utils/submissionCode';

export default function SubmissionCodeBlock({ code, language }: { code: string; language: string }) {
  const [highlighted, setHighlighted] = useState<{ code: string; language: string; html: string } | null>(null);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const copied = copiedCode === code;

  useEffect(() => {
    let cancelled = false;
    highlightSubmissionCode(code, language).then(html => {
      if (!cancelled) setHighlighted({ code, language, html });
    }).catch(() => {
      // Keep the original selectable source visible if the highlighter cannot load.
    });
    return () => { cancelled = true; };
  }, [code, language]);

  useEffect(() => {
    if (copiedCode === null) return;
    const timer = window.setTimeout(() => setCopiedCode(null), 2000);
    return () => window.clearTimeout(timer);
  }, [copiedCode]);

  const copyCode = async () => {
    try {
      await copySubmissionCode(code);
      setCopiedCode(code);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to copy code');
    }
  };

  const ready = highlighted?.code === code && highlighted.language === language;
  return (
    <div className="board-submission-code">
      <div className="board-submission-code-header">
        <span>Source Code{language ? ` · ${language}` : ''}</span>
        <Button className="board-submission-copy" type="text" size="small" icon={copied ? <CheckOutlined /> : <CopyOutlined />} onClick={() => void copyCode()}>
          {copied ? 'Copied' : 'Copy Code'}
        </Button>
      </div>
      <pre className="board-submission-code-content" tabIndex={0} aria-label="Submission source code">
        {/* highlight.js escapes source text; only its generated token markup is HTML. */}
        {ready ? <code className="hljs" dangerouslySetInnerHTML={{ __html: highlighted.html }} /> : <code>{code}</code>}
      </pre>
    </div>
  );
}
