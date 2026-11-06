export interface ExternalBoardConfig {
  configSection?: 'board' | 'resolver';
  boardId: number;
  name: string;
  eventId: number;
  isExternalOpen: boolean;
  isUnfrozen: boolean;
  actualStartTime: string | null;
  actualEndTime: string | null;
  freezeTime?: string | null;
  organizationDisplay?: 'school' | 'university';
  resolverEnabled?: boolean;
  resolverPasswordSet?: boolean;
  resolverPassword?: string;
  resolverSingleStep?: boolean;
  resolverSpeed?: number;
  resolverSettings?: {
    awards: { showMedals: boolean; showPodium?: boolean; championLabel?: string; runnerUpLabel?: string; secondRunnerUpLabel?: string; firstToSolve: boolean; solvedCount: boolean; bestFemaleTeam: boolean; bestFemaleLabel: string; goldLabel: string; silverLabel: string; bronzeLabel: string; customAwards: { citation: string; participantIds: number[]; displayMode: 'detail' | 'list' | 'photos' }[] };
    display: { rowsPerScreen: number; nameTemplate?: string; theme: 'dark' | 'light'; showOrganizationLogo: boolean; showTeamPhoto: boolean; showOrganization: boolean; showMembers: boolean };
  };
  medalConfig: {
    gold: number;
    silver: number;
    bronze: number;
  };
  problems: BoardProblemConfig[];
}

export interface BoardProblemConfig {
  displayId: string; 
  name: string;      
  score: number;
  timeLimit?: number;   
  memoryLimit?: number; 
}

export interface BoardVO {
    boardId: number;
    eventId: number;
    title: string;
    sessionName?: string;
    type: 'XCPC' | 'GPLT' | 'LANQIAO'; 
    status: 'PENDING' | 'RUNNING' | 'FROZEN' | 'ENDED';
    unfrozen?: boolean;
    isUnfrozen?: boolean;
    startTime: string;
    endTime: string;
    freezeTime?: string | null;
    isTeam?: number; 
    problems: {
        id: string;
        name: string;
        score: number;
        timeLimit?: number;
        memoryLimit?: number;
        
        solved: number;
        accepted: number;
        submitted: number;
        attempted: number;
        firstSolved: number;
        lastSolved: number;

        avgScore?: number;
        maxScore?: number;
        minScore?: number;
    }[];
    rows: RankItem[];
}

export interface BoardProblemInfo {
  id: string; 
  name: string;
  timeLimit?: number;   
  memoryLimit?: number; 
  score: number;
}

export interface MemberInfo {
  name: string;
  numberId: string;
}

export interface RankItem {
  rank: number;
  itemId: number; 
  name: string;   
  members?: string;
  memberInfos?: MemberInfo[];
  avatar?: string;
  photo?: string;
  photoThumbnail?: string;
  hasTeamPhoto?: boolean;
  advisor?: string;
  coach?: string;
  englishName?: string;
  firstSolveIds?: string[];
  organization?: string;
  organizationLogo?: string;
  isStar?: boolean;      
  isGirl?: boolean;
  medal?: 'Gold' | 'Silver' | 'Bronze';

  group?: string;

  solved?: number;
  penalty?: number;

  totalScore?: number;

  problemStatus: Record<string, ProblemStatus>;
}

export interface ProblemStatus {
  status: 'AC' | 'WA' | 'PENDING' | 'NONE' | 'Scored';
  score?: number;
  maxScore?: number;
  time?: number;
  timeMillis?: number;
  tryCount?: number;  
  isFirstAc?: boolean;
}

export interface BoardStats {
  totalSubmissions: number;
  problemCount: number;
  participantCount: number;
  organizationCount: number;
  heatmapData: { time: string; count: number }[];
  problemStats: Record<string, { ac: number; wa: number }>;
  submissionChart: { problemId: string; acCount: number; waCount: number }[];
  problemPassChart: { problemId: string; passCount: number }[];
  teamPassChart: { solvedCount: number; teamCount: number }[];
}

export interface SubmissionVO {
    submissionId: number; 
    displayId: string;   
    problemName: string;
    userName: string;
    teamName?: string;
    status: string;      
    score?: number;
    
    scoreDisplay?: string;
    
    time: number | string;
    memory: number | string;
    length?: number;
    language?: string;
    submitTime: string;
}
export interface ExternalBoardSummary {
  boardId: number;
  eventId: number;
  name: string;
  eventName: string;
  type: string;
  isTeam: number;
  signupCount: number;
  externalOpen: boolean;
  unfrozen: boolean;
  eventStart: string;
  eventEnd: string;
  actualStartTime?: string | null;
  actualEndTime?: string | null;
  freezeTime?: string | null;
}
