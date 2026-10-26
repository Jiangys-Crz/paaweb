export interface UserSearchResult {
  userId: number;
  userName?: string;
  realName?: string;
  avatarUrl?: string;
}

export const userDisplayName = (user: UserSearchResult) => user.userName || user.realName || '用户';
export const userSearchLabel = (user: UserSearchResult) => `${userDisplayName(user)} · #${user.userId}`;

const escapeHtml = (value: string) => value.replace(/[&<>"']/g, character => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[character]!));

/** Cards published in content contain public display fields only. */
export const userCardHtml = (user: UserSearchResult) => {
  const id = Number(user.userId);
  if (!Number.isSafeInteger(id) || id <= 0) return '';
  const avatar = escapeHtml(user.avatarUrl || '/avatar-default.svg');
  return ` <a href="/profile/${id}"><img src="${avatar}" width="20" height="20" alt="" /> ${escapeHtml(userDisplayName(user))}</a> `;
};
