'use client';

import type { ReactNode } from 'react';
import { Tag } from 'antd';
import type { RankItem } from '@/types/leaderboard';
import { formatAdvisor } from '@/utils/advisorNames';
import { getBoardParticipantKind } from '@/app/utils/boardParticipants';
import { BoardParticipantMarkers, BoardParticipantWatermark } from './BoardParticipantIdentity';

export type ParticipantCardRow = Pick<RankItem, 'name'> & Partial<Pick<RankItem,
  'rank' | 'organization' | 'organizationLogo' | 'group' | 'isStar' | 'isGirl' | 'members' | 'advisor' | 'coach'>>;

/** The same public identity card is used by the pre-contest board and signup list. */
export default function BoardParticipantCard({ row, isTeam, children }: { row: ParticipantCardRow; isTeam: boolean; children?: ReactNode }) {
  const kind = getBoardParticipantKind(row);
  const label = kind === 'star' ? (isTeam ? '打星队伍' : '打星参赛') : kind === 'girl' ? (isTeam ? '女生队伍' : '女生参赛') : isTeam ? '正式队伍' : '正式参赛';
  return <article className={`board-participant-card is-${kind}`} aria-label={`${row.name}，${label}`}>
    <BoardParticipantWatermark row={row} />
    <div className="board-participant-card-foreground">
      <div className="board-participant-card-heading"><strong>{row.name}</strong><BoardParticipantMarkers row={{ ...row, rank: row.rank ?? 0 }} /></div>
      <div className="board-participant-org">{row.organization || '—'}</div>
      <Tag color={kind === 'star' ? 'gold' : kind === 'girl' ? 'magenta' : 'blue'}>{label}</Tag>
      {row.group && !['star', 'girl', 'official'].includes(row.group) && <Tag>{row.group}</Tag>}
      {isTeam && <div>队员：{row.members?.replace(/[,，]\s*/g, '、') || '—'}</div>}
      <div>指导老师：{formatAdvisor(row.coach || row.advisor, '—')}</div>
      {children}
    </div>
  </article>;
}
