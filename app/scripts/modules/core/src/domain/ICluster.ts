import { IInstanceCounts } from './IInstanceCounts';
import { IServerGroup } from './IServerGroup';

export interface IClusterSummary {
  account: string;
  name: string;
}

export interface ICluster extends IClusterSummary {
  category: string;
  cloudProvider: string;
  instanceCounts?: IInstanceCounts;
  serverGroups: IServerGroup[];
}
