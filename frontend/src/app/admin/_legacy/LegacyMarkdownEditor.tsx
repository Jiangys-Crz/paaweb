"use client";

import { forwardRef, useImperativeHandle } from "react";
import { Input } from "antd";

export interface ExposeParam {
  insert: (handler: () => { targetValue: string; select?: boolean; deviationStart?: number; deviationEnd?: number }) => void;
}

interface MdEditorProps {
  modelValue?: string;
  onChange?: (value: string) => void;
  onUploadImg?: (files: File[], callback: (urls: string[]) => void) => void;
  style?: React.CSSProperties;
}

export const MdEditor = forwardRef<ExposeParam, MdEditorProps>(({ modelValue = "", onChange, style }, ref) => {
  useImperativeHandle(ref, () => ({
    insert: (handler) => {
      const result = handler();
      onChange?.(`${modelValue}${result.targetValue}`);
    },
  }));

  return (
    <Input.TextArea
      value={modelValue}
      onChange={(event) => onChange?.(event.target.value)}
      style={{ minHeight: 360, fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace", ...style }}
    />
  );
});

MdEditor.displayName = "MdEditor";
