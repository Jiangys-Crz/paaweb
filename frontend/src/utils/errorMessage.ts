import axios from 'axios';

/**
 * 把请求失败翻译成用户能看懂的原因。
 *
 * 关键点：axios 只有在收到 2xx 时才会走 then，凡是 4xx/5xx（接口不存在、限流、
 * 网关超时、服务不可用）都会直接抛错。如果 catch 里统一回一句“网络错误”，
 * 后端明明返回了「用户名已存在」「邮箱已被注册」这类具体原因，用户也看不到。
 *
 * 优先级：响应体里的业务提示（msg/message）> 常见状态码解释 > 传输层原因 > 兜底。
 */
export function describeRequestError(error: unknown, fallback = '提交失败，请稍后重试'): string {
  if (!axios.isAxiosError(error)) {
    return fallback;
  }

  const response = error.response;
  const data = response?.data as { msg?: unknown; message?: unknown; error?: unknown } | string | undefined;

  // 1) 后端/网关在非 2xx 响应里带的业务提示（如 {"code":429,"msg":"访问过于频繁，请稍后重试"}）
  if (data && typeof data === 'object') {
    const businessMessage = data.msg ?? data.message;
    if (typeof businessMessage === 'string' && businessMessage.trim()) {
      return businessMessage.trim();
    }
  }
  if (typeof data === 'string' && data.trim() && !data.trim().startsWith('<')) {
    return data.trim(); // 纯文本错误体；HTML 错误页不直接展示
  }

  // 2) 传输层原因：超时、断网
  if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
    return '请求超时，请检查网络后重试';
  }
  if (!response) {
    return '网络连接失败，请检查网络后重试';
  }

  // 3) 状态码解释
  switch (response.status) {
    case 400:
      return '提交内容不合法，请检查后重试';
    case 401:
      return '登录状态已失效，请重新登录';
    case 403:
      return '没有权限执行该操作';
    case 404:
      return '接口不存在或前后端版本不一致（HTTP 404），请联系管理员';
    case 405:
      return '请求方式不被支持（HTTP 405），请联系管理员';
    case 409:
      return '数据冲突，请刷新页面后重试';
    case 413:
      return '提交内容过大，请精简后重试';
    case 429:
      return '访问过于频繁，请稍后再试';
    case 502:
    case 503:
    case 504:
      return `服务暂时不可用（HTTP ${response.status}），请稍后重试`;
    default:
      return `请求失败（HTTP ${response.status}），请稍后重试或联系管理员`;
  }
}
