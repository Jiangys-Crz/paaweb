import * as XLSX from 'xlsx';

export type ExcelTemplateOptions = {
  fileName: string;
  sheetName?: string;
  headers: string[];
  examples?: Array<Array<string | number>>;
};

const normalizeCell = (value: unknown) => String(value ?? '').trim();

export function downloadExcelTemplate({
  fileName,
  sheetName = '导入模板',
  headers,
  examples = [],
}: ExcelTemplateOptions) {
  const worksheet = XLSX.utils.aoa_to_sheet([headers, ...examples]);
  worksheet['!cols'] = headers.map((header) => ({ wch: Math.max(14, header.length * 2 + 4) }));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, sheetName);
  XLSX.writeFile(workbook, fileName);
}

export async function readExcelRows(file: File, expectedHeaders: string[], alternateHeaders?: string[]): Promise<string[][]> {
  const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array', cellFormula: true });
  const firstSheetName = workbook.SheetNames[0];
  if (!firstSheetName) throw new Error('Excel 中没有可读取的工作表');

  const worksheet = workbook.Sheets[firstSheetName];
  for (const [address, cell] of Object.entries(worksheet)) {
    if (!address.startsWith('!') && typeof cell === 'object' && cell && 'f' in cell) {
      throw new Error(`单元格 ${address} 含有公式，请粘贴为纯文本后重新上传`);
    }
  }

  const values = XLSX.utils.sheet_to_json<unknown[]>(worksheet, {
    header: 1,
    defval: '',
    raw: false,
    blankrows: false,
  });
  if (values.length === 0) throw new Error('Excel 内容为空');

  const actualHeaders = (values[0] || []).slice(0, expectedHeaders.length).map(normalizeCell);
  if (actualHeaders.length !== expectedHeaders.length
      || expectedHeaders.some((header, index) => actualHeaders[index] !== header && actualHeaders[index] !== alternateHeaders?.[index])) {
    throw new Error(`表头不正确，应为：${expectedHeaders.join('、')}`);
  }

  return values.slice(1)
    .map((row) => expectedHeaders.map((_, index) => normalizeCell(row[index])))
    .filter((row) => row.some(Boolean));
}

export function rowsToTsv(rows: string[][]): string {
  return rows.map((row) => row.map((cell) => cell.replace(/[\t\r\n]+/g, ' ')).join('\t')).join('\n');
}
