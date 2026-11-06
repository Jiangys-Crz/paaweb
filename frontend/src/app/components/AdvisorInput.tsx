'use client';

import { Button, Input } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';

export { formatAdvisor as advisorText } from '@/utils/advisorNames';
import { advisorNames } from '@/utils/advisorNames';

export default function AdvisorInput({ value, onChange, id }: { value?: string[] | string; onChange?: (value: string[]) => void; id?: string }) {
  const values = Array.isArray(value) ? value : advisorNames(value);
  const rows = values.length ? values : [''];
  return <div id={id} style={{ display: 'grid', gap: 8 }}>
    {rows.map((name, index) => <div key={index} style={{ display: 'flex', gap: 8 }}>
      <Input value={name} placeholder="选填指导老师（可填班主任）" aria-label={`指导老师 ${index + 1}`} onChange={event => onChange?.(rows.map((item, position) => position === index ? event.target.value : item))} onKeyDown={event => { if (event.key === 'Enter') event.preventDefault(); }} />
      <Button danger type="text" icon={<DeleteOutlined />} aria-label={`删除指导老师 ${index + 1}`} disabled={rows.length === 1 && !name} onClick={() => onChange?.(rows.filter((_, position) => position !== index))} />
    </div>)}
    <Button type="dashed" icon={<PlusOutlined />} onClick={() => onChange?.([...rows, ''])}>添加指导老师</Button>
  </div>;
}
