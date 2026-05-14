/** 统一消息格式 — 所有渠道归一化 */
export interface UnifiedMessage {
  session_id: string;
  user_id: string;
  channel: "web" | "wechat" | "feishu" | "wecom";
  message_type: "text" | "image" | "file";
  content: string;
  context: { history_turns: number };
  timestamp: number;
}

/** 岗位标签 */
export interface JobTag {
  job_title: string;
  tags: string[];
  typical_companies: string[];
  salary_range_p50: string;
  difficulty: "低" | "中" | "高";
  description: string;
}

/** 专业→岗位映射 */
export interface MajorJobMapping {
  major: string;
  category: string;
  primary_jobs: JobTag[];
  extended_jobs: JobTag[];
}

/** 公司情报卡片 */
export interface CompanyIntel {
  id: string;
  name: string;
  aliases: string[];
  location: string;
  founded: string;
  scale: string;
  funding_stage: string;
  industry: string;
  website: string;
  apply_url: string;
  jd_summary?: string;
  salary_info?: SalaryInfo;
  reviews?: ReviewAggregation;
  timeline?: CompanyEvent[];
}

/** 薪资信息 */
export interface SalaryInfo {
  p25: number;
  p50: number;
  p75: number;
  currency: string;
  components: {
    base: string;
    bonus: string;
    equity: string;
  };
  city_comparison: Record<string, number>;
  benefits: string[];
  data_source: string;
  data_date: string;
}

/** 评价聚合 */
export interface ReviewAggregation {
  dimensions: {
    name: string;
    score: number;
    summary: string;
    sources: string[];
  }[];
  overall_score: number;
}

/** 公司事件 */
export interface CompanyEvent {
  date: string;
  type: "funding" | "product" | "org" | "news";
  title: string;
  description: string;
}

/** 简历结构化对象 */
export interface Resume {
  id: string;
  user_id: string;
  basic_info: {
    name: string;
    phone: string;
    email: string;
    education: string;
    target_position: string;
  };
  education: EducationEntry[];
  projects: ProjectEntry[];
  internships: InternshipEntry[];
  skills: string[];
  honors: string[];
  self_evaluation: string;
  raw_file_url?: string;
  versions: ResumeVersion[];
}

export interface EducationEntry {
  school: string;
  major: string;
  degree: string;
  start_date: string;
  end_date: string;
  gpa?: string;
}

export interface ProjectEntry {
  name: string;
  role: string;
  tech_stack: string[];
  description: string;
  achievements: string[];
}

export interface InternshipEntry {
  company: string;
  position: string;
  start_date: string;
  end_date: string;
  description: string;
  achievements: string[];
}

export interface ResumeVersion {
  version_id: number;
  created_at: string;
  change_summary: string;
}

/** 简历评分结果 */
export interface ResumeScore {
  overall_match: number;
  dimensions: {
    name: string;
    score: number;
    feedback: string;
  }[];
  highlights: string[];
  weaknesses: string[];
  suggestions: string[];
}

/** 求职进度记录 */
export interface TrackerRecord {
  id: string;
  user_id: string;
  company_name: string;
  company_id?: string;
  position: string;
  apply_date: string;
  status: TrackerStatus;
  notes: string;
  deadline?: string;
  next_action?: string;
}

export type TrackerStatus =
  | "interested"
  | "applied"
  | "screening"
  | "assessment"
  | "interviewing"
  | "waiting"
  | "offer"
  | "rejected";

/** 技能差距分析结果 */
export interface SkillGapResult {
  matched: string[];
  need_improvement: { skill: string; reason: string; resources: string[] }[];
  missing: { skill: string; reason: string; resources: string[] }[];
}
