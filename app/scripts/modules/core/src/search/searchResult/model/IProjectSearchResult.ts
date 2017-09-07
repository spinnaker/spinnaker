import { IProjectConfig } from 'core/domain';

export interface IProjectSearchResult {
  config: IProjectConfig;
  applications: string[];
  clusters: string[];
  pipelineConfigId: string;
  createTs: number;
  displayName: string;
  email: string;
  href: string;
  id: string;
  lastModifiedBy: string;
  name: string;
  type: string;
  updateTs: number;
  url: string;
}
