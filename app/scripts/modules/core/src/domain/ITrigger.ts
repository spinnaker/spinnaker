import { IExecution, ITemplateInheritable } from 'core/domain';

export interface ITrigger extends ITemplateInheritable {
  enabled: boolean;
  rebake?: boolean;
  user?: string;
  type: string;
  expectedArtifactIds?: string[]; // uuid references to ExpectedArtifacts defined in the Pipeline.
  runAsUser?: string;
  excludedArtifactTypePatterns?: RegExp[];
}

export interface IArtifactoryTrigger extends ITrigger {
  artifactorySearchName: string;
  artifactoryRepository: string;
  type: 'artifactory';
}

export interface IGitTrigger extends ITrigger {
  source: string;
  secret?: string;
  project: string;
  slug: string;
  branch: string;
  hash?: string;
  type: 'git';
}

export interface IBuildTrigger extends ITrigger {
  buildInfo?: any;
  buildNumber?: number;
  job: string;
  project: string;
  propertyFile?: string;
  master: string;
  type: 'jenkins' | 'travis' | 'wercker' | 'concourse';
}

export interface IWerckerTrigger extends IBuildTrigger {
  app: string;
  pipeline: string;
  type: 'wercker';
}

export interface IConcourseTrigger extends IBuildTrigger {
  // Concourse pipeline is represented by project
  team: string;
  jobName: string; // job will be the concatenation of team/pipeline/jobName
  type: 'concourse';
}

export interface IPipelineTrigger extends ITrigger {
  application: string;
  parentExecution?: IExecution;
  parentPipelineId?: string;
  pipeline: string;
  status: string[];
}

export interface ICronTrigger extends ITrigger {
  cronExpression: string;
}

export interface IPubsubTrigger extends ITrigger {
  pubsubSystem: string;
  subscriptionName: string;
  payloadConstraints: { [key: string]: string };
  attributeConstraints: { [key: string]: string };
}

export interface IWebhookTrigger extends ITrigger {
  source: string;
  payloadConstraints: { [key: string]: string };
}

export interface IWerckerTrigger extends IBuildTrigger {
  app: string;
  pipeline: string;
  type: 'wercker';
}
