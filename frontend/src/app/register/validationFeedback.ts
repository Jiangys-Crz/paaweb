/**
 * 注册表单的校验失败提示。
 *
 * antd 的 Form.validateFields 在校验不通过时会 reject 一个带 errorFields 的对象，
 * 如果只写成“请先完善当前步骤”，用户根本不知道是哪个格子有问题。这里把它翻译成
 * 一条能指明「字段 + 原因」的提示，并给出需要滚动定位的字段路径。
 */

/** 字段名 → 中文名 */
export const FIELD_LABELS: Record<string, string> = {
  userAccount: '用户名',
  password: '密码',
  confirmPassword: '确认密码',
  userName: '昵称',
  email: '邮箱',
  phone: '手机号',
  eduBg: '身份类型',
  realName: '真实姓名',
  numberId: '学号/工号',
  gender: '性别',
  universityId: '学校/单位',
  schoolId: '学院',
  majorId: '专业',
  gradeYear: '入学年份',
  className: '行政班级',
  verifyCode: '邮箱验证码',
};

export interface ValidationErrorInfo {
  name?: Array<string | number>;
  errors?: string[];
}

export interface ValidationFeedback {
  /** 展示给用户的提示文案 */
  message: string;
  /** 需要滚动/聚焦的字段路径（antd 的 name 数组） */
  field?: Array<string | number>;
  /** 失败字段数量，便于统计或一次性高亮 */
  fieldCount: number;
}

/**
 * 把 antd 的校验失败对象转成提示；传入的不是校验失败对象时返回 null，
 * 调用方据此回落到网络/接口错误处理。
 */
export function describeValidationFailure(error: unknown): ValidationFeedback | null {
  const errorFields = (error as { errorFields?: ValidationErrorInfo[] } | null)?.errorFields;
  if (!Array.isArray(errorFields) || errorFields.length === 0) {
    return null;
  }

  const first = errorFields[0];
  const fieldName = String(first?.name?.[0] ?? '');
  const label = FIELD_LABELS[fieldName] || '该项';
  const detail = first?.errors?.[0] || '填写有误';
  // 规则里已经带了字段名（如“真实姓名不能为空”）时不再重复前缀
  const text = detail.includes(label) ? detail : `${label}：${detail}`;
  const rest = errorFields.length - 1;

  return {
    message: rest > 0 ? `${text}（另有 ${rest} 项需要修改）` : text,
    field: Array.isArray(first?.name) && first.name.length > 0 ? first.name : undefined,
    fieldCount: errorFields.length,
  };
}
