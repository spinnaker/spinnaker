export interface ITrigger {
  enabled: boolean;
  user?: string;
  type: string;
}

export interface IGitTrigger extends ITrigger {
  source: string;
  project: string;
  slug: string;
  branch: string;
  type: 'git';
}

export interface IJenkinsTrigger extends ITrigger {
  job: string;
  master: string;
  type: 'jenkins';
}

export interface IPipelineTrigger extends ITrigger {
  application: string;
  pipeline: string;
}
