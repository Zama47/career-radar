export type Seniority = "INTERN" | "JUNIOR" | "MIDDLE" | "SENIOR" | "LEAD" | "UNKNOWN";

export type RemoteType = "OFFICE" | "HYBRID" | "REMOTE" | "UNKNOWN";

export type VacancyStatus = "ACTIVE" | "ARCHIVED" | "DUPLICATE";

export type SalaryPeriod = "MONTH" | "YEAR" | "DAY" | "HOUR" | "UNKNOWN";

export type SkillSource = "RULE" | "LLM" | "MANUAL";

export type VacancyListItem = {
  id: string;
  title: string;
  company?: string | null;
  profession?: string | null;
  seniority: Seniority;
  remoteType: RemoteType;
  location?: string | null;
  salaryFrom?: number | null;
  salaryTo?: number | null;
  currency?: string | null;
  salaryGross?: boolean | null;
  salaryPeriod?: SalaryPeriod;
  publishedAt?: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
  status: VacancyStatus;
  url?: string | null;
};

export type VacancyPage = {
  items: VacancyListItem[];
  page: number;
  size: number;
  total: number;
};

export type VacancySkill = {
  code: string;
  name: string;
  confidence: number;
  source: SkillSource;
};

export type VacancyDetail = VacancyListItem & {
  description?: string | null;
  companyWebsite?: string | null;
  professionName?: string | null;
  sourceUrl?: string | null;
  source?: string | null;
  sourceName?: string | null;
  skills: VacancySkill[];
};

export type TopProfession = {
  code: string;
  count: number;
};

export type TopSkill = {
  name: string;
  count: number;
};

export type Dashboard = {
  totalVacancies: number;
  newToday: number;
  internships: number;
  remoteShare: number;
  topProfessions: TopProfession[];
  topSkills: TopSkill[];
};

export type AnalyticsTopProfession = {
  code: string;
  name: string;
  count: number;
};

export type AnalyticsTopSkill = {
  name: string;
  count: number;
};

export type AnalyticsOverview = {
  totalVacancies: number;
  newVacancies: number;
  medianSalary?: number | null;
  p25Salary?: number | null;
  p75Salary?: number | null;
  remoteShare: number;
  currency?: string | null;
  topProfessions: AnalyticsTopProfession[];
  topSkills: AnalyticsTopSkill[];
};

export type TimelinePoint = {
  date: string;
  count: number;
};

export type AnalyticsTimeline = {
  points: TimelinePoint[];
};

export type SkillStat = {
  skill: string;
  count: number;
  share: number;
};

export type AnalyticsSkills = {
  items: SkillStat[];
};

export type SalaryStats = {
  p25?: number | null;
  median?: number | null;
  p75?: number | null;
  currency?: string | null;
  sampleSize: number;
};

export type TimelineBucket = "day" | "week" | "month";

export type Profession = {
  code: string;
  name: string;
};

export type LoginResponse = {
  accessToken: string;
  expiresIn: number;
};

export type ResumeSummary = {
  id: string;
  title: string;
  isMaster: boolean;
  hasPdf: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ResumeDetail = ResumeSummary & {
  contentJson: Record<string, unknown>;
  contentText: string;
};

export type AiTokens = {
  input: number;
  output: number;
};

export type FitVerdict =
  | "strong_match"
  | "good_match"
  | "partial_match"
  | "weak_match";

export type FitResult = {
  score: number;
  verdict: FitVerdict;
  strengths: string[];
  missing: string[];
  reasons: string[];
  model: string;
  tokens: AiTokens;
  cached: boolean;
};

export type CoverLetterDraft = {
  subject: string;
  text: string;
  model: string;
  tokens: AiTokens;
  cached: boolean;
};

export type TailorDraft = {
  contentJson: Record<string, unknown>;
  diff: string[];
  warnings: string[];
  model: string;
  tokens: AiTokens;
  cached: boolean;
};
