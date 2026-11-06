'use client';

import { compareBoardRank } from '@/app/utils/boardParticipants';
import { formatAdvisor } from '@/utils/advisorNames';
import React, { useEffect, useRef, useState } from 'react';
import type { RankItem } from '@/types/leaderboard';
import './ResolverAwards.css';

export type ResolverAwardRow = RankItem & { photo?: string; photoThumbnail?: string; hasTeamPhoto?: boolean; englishName?: string; coach?: string; firstSolveIds?: string[]; displayRank?: number | string; resolved?: number; cost?: number };
export type ResolverAward = { citation: string; type: string };
export type ResolverAwardDisplay = { theme?: 'dark' | 'light'; showOrganizationLogo?: boolean; showTeamPhoto?: boolean; showOrganization?: boolean; showMembers?: boolean };
type Shared = { display?: ResolverAwardDisplay };

const medalNames = {
  Gold: { chinese: '金奖', english: 'Gold', number: '1', outer: '#dc9905', inner: '#f8df7a', ink: '#936b06' },
  Silver: { chinese: '银奖', english: 'Silver', number: '2', outer: '#87909a', inner: '#e4e9ec', ink: '#65717e' },
  Bronze: { chinese: '铜奖', english: 'Bronze', number: '3', outer: '#b34f2a', inner: '#e5a587', ink: '#773420' },
} as const;
type MedalKind = keyof typeof medalNames;
const podiumKinds: Record<string, MedalKind> = { Champion: 'Gold', 'Runner-up': 'Silver', 'Second Runner-up': 'Bronze' };

function MedalIllustration({ medal }: { medal?: MedalKind }) {
  const color = medal ? medalNames[medal] : { outer: '#9ba5b2', inner: '#e1e6ea', ink: '#667485', number: '' };
  return <svg className="resolver-medal-illustration" viewBox="0 0 540 700" aria-hidden="true">
    <path fill="#2d426d" d="M155 18h92l38 240h-80z"/><path fill="#1f335a" d="M308 18h92l-48 240h-80z"/>
    <path d="M201 18l44 236M356 18l-44 236" fill="none" stroke="#8291ac" strokeWidth="3" opacity=".6"/>
    <circle cx="270" cy="260" r="27" fill="none" stroke={color.outer} strokeWidth="12"/>
    <circle cx="270" cy="470" r="216" fill={color.outer}/><circle cx="270" cy="470" r="201" fill={color.inner} stroke={color.ink} strokeOpacity=".42" strokeWidth="2"/>
    <circle cx="270" cy="470" r="169" fill="none" stroke={color.ink} strokeOpacity=".46" strokeWidth="2"/>
    <circle cx="270" cy="470" r="80" fill={color.ink}/><circle cx="270" cy="470" r="68" fill="none" stroke={color.inner} strokeWidth="2"/>
    <text x="270" y="510" textAnchor="middle" fill={color.inner} fontSize="112" fontFamily="sans-serif" fontWeight="700">{color.number}</text>
    <g fill={color.ink} opacity=".8">
      {[0,1,2,3,4,5,6].map(i => <ellipse key={`l${i}`} cx={155 + i * 12} cy={425 + i * 26} rx="8" ry="20" transform={`rotate(${35-i*8} ${155+i*12} ${425+i*26})`} />)}
      {[0,1,2,3,4,5,6].map(i => <ellipse key={`r${i}`} cx={385 - i * 12} cy={425 + i * 26} rx="8" ry="20" transform={`rotate(${-35+i*8} ${385-i*12} ${425+i*26})`} />)}
    </g><path d="M76 402q28-78 107-117" fill="none" stroke="#fff4bb" strokeWidth="7" strokeLinecap="round" opacity=".7"/>
  </svg>;
}

function AwardVisual({ photo, medal }: { photo?: string | false; medal?: MedalKind }) {
  const [loaded, setLoaded] = useState(false);
  const [failed, setFailed] = useState(false);
  const fadeFrame = useRef(0);
  useEffect(() => () => cancelAnimationFrame(fadeFrame.current), []);
  const reveal = () => {
    fadeFrame.current = requestAnimationFrame(() => {
      fadeFrame.current = requestAnimationFrame(() => setLoaded(true));
    });
  };
  return <div className="resolver-award-visual">{photo && !failed
    ? <img className={`resolver-award-view-photo${loaded ? ' loaded' : ''}`} src={photo} alt="" onLoad={reveal} onError={() => setFailed(true)} />
    : <MedalIllustration medal={medal} />}</div>;
}

export function OverflowText({ value, className = '' }: { value: string; className?: string }) {
  const viewportRef = useRef<HTMLDivElement>(null);
  const textRef = useRef<HTMLDivElement>(null);
  const [overflow, setOverflow] = useState({ distance: 0, fit: 1 });
  useEffect(() => {
    const viewport = viewportRef.current, text = textRef.current;
    if (!viewport || !text) return;
    const measure = () => {
      const width = text.scrollWidth, available = viewport.clientWidth;
      const next = { distance: Math.max(0, width - available), fit: width ? Math.min(1, available / width) : 1 };
      setOverflow(previous => previous.distance === next.distance && previous.fit === next.fit ? previous : next);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(viewport);
    observer.observe(text);
    document.fonts?.addEventListener('loadingdone', measure);
    return () => { observer.disconnect(); document.fonts?.removeEventListener('loadingdone', measure); };
  }, [value]);
  const style = { '--overflow-distance': `${overflow.distance}px`, '--overflow-fit': overflow.fit, '--overflow-duration': `${Math.max(5, overflow.distance / 32 * 2 + 2)}s` } as React.CSSProperties;
  return <div ref={viewportRef} className={`resolver-overflow ${className}${overflow.distance > 0 ? ' is-overflowing' : ''}`} style={style}><div ref={textRef} className="resolver-overflow-text">{value}</div></div>;
}

function AwardField({ label, english, value, prominent = false }: { label: string; english: string; value: string; prominent?: boolean }) {
  return <div className={`resolver-award-field${prominent ? ' prominent' : ''}`}><div className="resolver-award-field-heading"><span>{label}</span><span>{english}</span></div><OverflowText className="resolver-award-field-value" value={value} /></div>;
}

export function ResolverAwardView({ row, citations = [], awards, medal, display }: Shared & { row: ResolverAwardRow; citations?: string[]; awards?: ResolverAward[]; medal?: string | null; showInfo?: boolean }) {
  const shownAwards = (awards ?? citations.map(citation => ({ citation, type: 'Other' }))).filter(award => award.type !== 'First to Solve');
  const medalKind = medal && Object.hasOwn(medalNames, medal) ? medal as MedalKind : undefined;
  const visualKind = medalKind || shownAwards.map(award => podiumKinds[award.type]).find(Boolean);
  const photo = display?.showTeamPhoto !== false && row.hasTeamPhoto === true && (row.photo || row.photoThumbnail);
  const logo = display?.showOrganizationLogo !== false && row.organizationLogo;
  const members = row.memberInfos?.map(member => member.name).filter(Boolean).join('、') || row.members;
  const firstSolve = row.firstSolveIds?.join(', ');
  return <section className="resolver-award-view" aria-label={`${row.name} award`}>
    <AwardVisual key={photo || `${row.itemId}-${visualKind || "none"}`} photo={photo} medal={visualKind} />
    <aside className="resolver-award-panel">
      {logo && <img className="resolver-award-watermark" src={logo} alt="" />}
      <div className="resolver-award-fields">
        {display?.showOrganization !== false && row.organization && <AwardField label="组织" english="ORGANIZATION" value={row.organization} prominent />}
        <AwardField label="队名" english="TEAM" value={row.name} />
        {row.englishName && <AwardField label="第二语言队名" english="ALT. TEAM NAME" value={row.englishName} />}
        {display?.showMembers !== false && members && <AwardField label="成员" english="MEMBERS" value={members} />}
        <AwardField label="指导老师" english="COACH" value={formatAdvisor(row.coach || row.advisor, '无')} />
        {shownAwards.length > 0 && <div className="resolver-award-field resolver-award-prizes"><div className="resolver-award-field-heading"><span>奖项</span><span>AWARDS</span></div>
          {shownAwards.map((award, index) => {
            const medalLabel = award.type === 'Medal' && medalKind ? medalNames[medalKind] : undefined;
            const standard = medalLabel && /^(gold|silver|bronze)( medal)?$|^(金奖|银奖|铜奖)$|^(金牌|银牌|铜牌)$/i.test(award.citation.trim());
            const female = award.type === 'Best Female Team';
            const citation = female && /^(?:best\s+female(?:\s+team)?|最佳女队(?:奖)?(?:\s+best\s+female(?:\s+team)?)?)$/i.test(award.citation.trim()) ? '最佳女队 Best Female' : award.citation;
            const plaqueKind = medalLabel ? medalKind : podiumKinds[award.type];
            return <div className={`resolver-award-plaque ${plaqueKind ? plaqueKind.toLowerCase() : female ? 'female' : 'other'}`} key={`${award.citation}-${index}`}>
              <svg className="resolver-award-plaque-ornament" viewBox="0 0 400 80" preserveAspectRatio="none" aria-hidden="true"><g fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M2 22V5h52M2 58v17h52M398 22V5h-52M398 58v17h-52"/><path d="M6 20c6-1 3-9 10-9 5 0 4 6 8 6 4 0 3-6 8-6 7 0 4 8 10 9-6 0-3 8-10 8-5 0-4-6-8-6-4 0-3 6-8 6-7 0-4-8-10-8z"/><path d="M358 20c6-1 3-9 10-9 5 0 4 6 8 6 4 0 3-6 8-6 7 0 4 8 10 9-6 0-3 8-10 8-5 0-4-6-8-6-4 0-3 6-8 6-7 0-4-8-10-8z"/><path d="M6 60c6-1 3-9 10-9 5 0 4 6 8 6 4 0 3-6 8-6 7 0 4 8 10 9-6 0-3 8-10 8-5 0-4-6-8-6-4 0-3 6-8 6-7 0-4-8-10-8z"/><path d="M358 60c6-1 3-9 10-9 5 0 4 6 8 6 4 0 3-6 8-6 7 0 4 8 10 9-6 0-3 8-10 8-5 0-4-6-8-6-4 0-3 6-8 6-7 0-4-8-10-8z"/></g></svg>
              <OverflowText className="resolver-award-plaque-text" value={[standard ? medalLabel!.chinese : citation, medalLabel && (standard || !award.citation.toLowerCase().includes(medalLabel.english.toLowerCase())) ? medalLabel.english : null].filter(Boolean).join(" ")} />
            </div>;
          })}
        </div>}
      </div>
      <div className="resolver-award-stats"><div className={`resolver-award-rank${!row.isStar && visualKind ? ` ${visualKind.toLowerCase()}` : ''}`}><span>正式排名<small>OFFICIAL RANK</small></span><strong>{row.isStar || row.rank === -1 ? '*' : row.displayRank ?? row.rank}</strong></div><div><span>解题数<small>SOLVED</small></span><strong>{row.resolved ?? row.solved ?? 0}</strong></div>{firstSolve && <div><span>首答<small>FIRST SOLVE</small></span><OverflowText className="resolver-award-stats-value" value={firstSolve} /></div>}</div>
    </aside>
  </section>;
}

export function ResolverGroupAwardView({ rows, citation, photos, medal, display, scrollPaused = false, scrollToTop = true, speed = 1, selections }: Shared & { rows: ResolverAwardRow[]; citation: string; photos: boolean; medal?: string | null; scrollPaused?: boolean; scrollToTop?: boolean; speed?: number; selections?: Record<string, string> }) {
  const orderedRows = [...rows].sort(compareBoardRank);
  const listRef = useRef<HTMLDivElement>(null);
  const position = useRef(0);
  const motion = useRef({ scrollPaused, speed });
  motion.current = { scrollPaused, speed };
  const ref = useRef<HTMLElement>(null);
  const [size, setSize] = useState({ width: 1920, height: 1080 });
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const update = () => setSize({ width: element.clientWidth, height: element.clientHeight });
    update();
    const observer = new ResizeObserver(update);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const { width, height } = size;
  const gap = Math.max(1, Math.floor(height / 120));
  const headerHeight = Math.floor(height / 20);
  const columns = Math.max(1, Math.ceil(Math.sqrt(rows.length)));
  const rowCount = Math.max(1, Math.ceil(rows.length / columns));
  const tileWidth = Math.max(0, Math.floor((width - (columns - 1) * gap - 1) / columns));
  const tileHeight = Math.max(0, Math.floor(columns === 2 && rowCount === 1 ? (height - headerHeight - 2 * gap - 1) / 2 : (height - headerHeight - gap - (rowCount - 1) * gap - 1) / rowCount));
  const nativeRowHeight = Math.max(1, Math.floor((height - 40) / 15));
  const rowHeight = nativeRowHeight * 1.2;
  const listTarget = rows.length + Math.floor(height / nativeRowHeight);
  const listSpeed = medal && Object.hasOwn(medalNames, medal) ? 1.8 : 1;
  useEffect(() => {
    if (photos || !listRef.current) return;
    const initial = position.current;
    const target = scrollToTop ? 0 : listTarget;
    const distance = Math.abs(target - initial);
    const direction = Math.sign(target - initial);
    const acceleration = .5, maxSpeed = .75;
    const ramp = Math.min(maxSpeed / acceleration, Math.sqrt(distance / acceleration));
    const coast = Math.max(0, (distance - acceleration * ramp * ramp) / (acceleration * ramp || 1));
    const duration = 2 * ramp + coast;
    let elapsed = 0, previous: number | undefined, id: number;
    const animate = (now: number) => {
      if (previous !== undefined && !motion.current.scrollPaused) elapsed += (now - previous) / 1000 * listSpeed * Math.max(.25, Math.min(4, motion.current.speed));
      previous = now;
      const t = Math.min(duration, elapsed);
      const traveled = t < ramp ? acceleration * t * t / 2 : t < ramp + coast
        ? acceleration * ramp * ramp / 2 + acceleration * ramp * (t - ramp)
        : distance - acceleration * (duration - t) ** 2 / 2;
      position.current = initial + direction * traveled;
      if (listRef.current) listRef.current.style.transform = `translateY(${-position.current * rowHeight}px)`;
      if (t < duration) id = requestAnimationFrame(animate);
    };
    id = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(id);
  }, [photos, scrollToTop, listTarget, rowHeight, listSpeed]);
  const groupStyle = { '--resolver-group-header': `${headerHeight}px`, '--resolver-group-gap': `${gap}px`, '--resolver-group-title': `${height * (photos ? .045 : .075)}px`, '--resolver-group-font': `${height * .0465}px`, '--resolver-group-row': `${rowHeight}px`, '--resolver-photo-font': `${height * .075 * tileHeight / 485}px`, '--resolver-tile-width': `${tileWidth}px`, '--resolver-tile-height': `${tileHeight}px` } as React.CSSProperties;
  return <section ref={ref} className="resolver-group-view" style={groupStyle} aria-label={citation}>
    <div className={`resolver-group-title ${photos ? 'photo-title' : ''}`}>{citation}</div>
    {photos ? <div className="resolver-group-photos" style={{ top: headerHeight + gap }}>
      {Array.from({ length: rowCount }, (_, index) => orderedRows.slice(index * columns, (index + 1) * columns)).map((group, index) => <div className="resolver-photo-row" key={index}>
        {group.map(row => <div className="resolver-photo-tile" key={row.itemId}>
          <AwardVisual photo={display?.showTeamPhoto !== false && row.hasTeamPhoto === true && (tileWidth > 320 || tileHeight > 320 ? row.photo || row.photoThumbnail : row.photoThumbnail || row.photo)} medal={row.medal && Object.hasOwn(medalNames, row.medal) ? row.medal as MedalKind : undefined} />
          <div className="resolver-photo-caption">{display?.showOrganizationLogo !== false && row.organizationLogo && <img src={row.organizationLogo} alt="" />}<span>{row.name}</span></div>
        </div>)}
      </div>)}
    </div> : <div className="resolver-group-list-window" style={{ top: height * .09 }}>
      <div ref={listRef} className="resolver-group-list">
        {orderedRows.map(row => <div className={`resolver-group-list-row ${selections?.[String(row.itemId)] ? `selected-${selections[String(row.itemId)]}` : ""}`} key={row.itemId}>{display?.showOrganizationLogo !== false && row.organizationLogo && <img src={row.organizationLogo} alt="" />}<span>{row.name}</span></div>)}
      </div>
    </div>}
  </section>;
}
