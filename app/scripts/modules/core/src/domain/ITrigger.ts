import { IExecution } from 'core/domain';

export interface ITrigger {
  enabled: boolean;
  rebake?: boolean;
  user?: string;
  type: string;
  expectedArtifactIds?: string[]; // uuid references to ExpectedArtifacts defined in the Pipeline.
  runAsUser?: string;
}

export interface IArtifactoryTrigger extends ITrigger {
  artifactorySearchName: string;
  artifactoryRepository: string;
  type: 'artifactory';
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
  buildInfo?: any;
  buildNumber?: number;
  job: string;
  project: string;
  master: string;
  type: 'jenkins' | 'travis' | 'wercker';
}

export interface IPipelineTrigger extends ITrigger {
  application: string;
  parentExecution?: IExecution;
  parentPipelineId?: string;
  pipeline: string;
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
