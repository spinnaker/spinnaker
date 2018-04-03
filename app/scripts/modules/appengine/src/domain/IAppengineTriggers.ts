export interface IAppengineGitTrigger {
  source: string;
  project: string;
  slug: string;
  branch: string;
}

export interface IAppengineJenkinsTrigger {
  master: string;
  job: string;
  matchBranchOnRegex?: string;
}
