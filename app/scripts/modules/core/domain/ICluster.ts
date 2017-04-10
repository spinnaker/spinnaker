import {ServerGroup} from './serverGroup';
import {InstanceCounts} from './instanceCounts';

export interface IClusterSummary {
  account: string;
  name: string;
}

export interface ICluster extends IClusterSummary {
  cloudProvider: string;
  category: string;
  serverGroups: ServerGroup[];
  instanceCounts?: InstanceCounts;
}
