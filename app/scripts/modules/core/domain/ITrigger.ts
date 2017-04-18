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
  hash?: string;
  type: 'git';
}

export interface IBuildTrigger extends ITrigger {
  job: string;
  master: string;
  type: 'jenkins' | 'travis';
}

export interface IPipelineTrigger extends ITrigger {
  application: string;
  pipeline: string;
}

export interface ICronTrigger extends ITrigger {
  cronExpression: string;
}
