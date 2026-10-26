"use client";

import React from 'react';
import { Alert, Table, Tag } from 'antd';

type PreviewEntry = string | Record<string, unknown>;

export type ImportPreviewData = {
  success?: number;
  failure?: number;
  imported?: number;
  updated?: number;
  successList?: PreviewEntry[];
  failedList?: PreviewEntry[];
  errors?: PreviewEntry[];
};

const describe = (item: PreviewEntry) => {
  if (typeof item === 'string') return item;
  const preferred = ['content', 'schoolName', 'numberId', 'realName', 'teamName'];
  const values = preferred.map((key) => item[key]).filter((value) => value !== undefined && value !== '');
  return values.length ? values.join(' / ') : Object.values(item).filter(Boolean).join(' / ');
};

const reason = (item: PreviewEntry) => typeof item === 'string' ? item : String(item.reason ?? '');

const ImportPreview: React.FC<{ data: ImportPreviewData }> = ({ data }) => {
  const successes = data.successList || [];
  const failures = data.failedList || data.errors || [];
  const successCount = data.success ?? ((data.imported || 0) + (data.updated || 0));
  const failureCount = data.failure ?? failures.length;
  const rows = [
    ...successes.map((item, index) => ({ key: `success-${index}`, status: 'success', content: describe(item), reason: reason(item) })),
    ...failures.map((item, index) => ({ key: `failure-${index}`, status: 'failure', content: describe(item), reason: reason(item) })),
  ];

  return (
    <div style={{ marginTop: 16 }}>
      <Alert
        type={failureCount > 0 ? 'warning' : 'success'}
        showIcon
        message={`校验完成：${successCount} 条可导入，${failureCount} 条不可导入`}
        description="确认后仅导入校验通过的数据；不可导入的数据不会写入系统。"
      />
      <Table
        style={{ marginTop: 12 }}
        size="small"
        pagination={{ pageSize: 6, hideOnSinglePage: true }}
        scroll={{ y: 260 }}
        dataSource={rows}
        columns={[
          { title: '校验结果', dataIndex: 'status', width: 92, render: (value) => value === 'success' ? <Tag color="success">可导入</Tag> : <Tag color="error">不可导入</Tag> },
          { title: '数据', dataIndex: 'content', ellipsis: true },
          { title: '说明', dataIndex: 'reason', width: 200, ellipsis: true },
        ]}
      />
    </div>
  );
};

export default ImportPreview;
