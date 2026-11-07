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

export type Profession = {
  code: string;
  name: string;
};

export type LoginResponse = {
  accessToken: string;
  expiresIn: number;
};
