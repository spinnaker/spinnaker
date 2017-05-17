import { IInstanceCounts } from './IInstanceCounts';
import { IServerGroup } from './IServerGroup';

export interface IClusterSummary {
  account: string;
  name: string;
}

export interface ICluster extends IClusterSummary {
  cloudProvider: string;
  category: string;
  serverGroups: IServerGroup[];
  instanceCounts?: IInstanceCounts;
}
