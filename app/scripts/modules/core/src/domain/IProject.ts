export interface IProjectCluster {
  account: string;
  stack: string;
  detail: string;
  applications: string[];
}

export interface IProjectPipeline {
  application: string;
  pipelineConfigId: string;
}

export interface IProjectConfig {
  applications: string[];
  clusters: IProjectCluster[];
  pipelineConfigs: IProjectPipeline[];
}

export interface IProject {
  config: IProjectConfig;
  email: string;
  id: string;
  name: string;
  notFound: boolean;
}
