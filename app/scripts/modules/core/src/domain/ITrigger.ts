export interface ITrigger {
  enabled: boolean;
  user?: string;
  type: string;
  expectedArtifactIds?: string[]; // uuid references to ExpectedArtifacts defined in the Pipeline.
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

export interface IDockerTrigger extends ITrigger {
  tag: string;
  repository: string;
}

export interface IPipelineTrigger extends ITrigger {
  application: string;
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
