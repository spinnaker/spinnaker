import { IInstanceCounts } from './IInstanceCounts';
import { IServerGroup } from './IServerGroup';

export interface IClusterSummary {
  account: string;
  name: string;
}

export interface ICluster extends IClusterSummary {
  category: string;
  containers?: any[];
  initContainers?: any[];
  cloudProvider: string;
  imageArtifactId?: string;
  imageSource?: string;
  instanceCounts?: IInstanceCounts;
  serverGroups: IServerGroup[];
}
