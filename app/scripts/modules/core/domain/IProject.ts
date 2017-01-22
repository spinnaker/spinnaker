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
}

export interface IProject {
  id: string;
  name: string;
  email: string;
  config: IProjectConfig;
  notFound: boolean;
}
