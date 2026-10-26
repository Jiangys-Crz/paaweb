/** QQ 机器人涉及服务端凭据和群消息发送，不能通过普通权限项授权。 */
export function canManageQqBot(user: { role?: string; accountStatus?: string } | null | undefined): boolean {
  return Boolean(user && user.accountStatus === 'normal'
    && (user.role === 'super_admin' || user.role === 'supper_admin'));
}
