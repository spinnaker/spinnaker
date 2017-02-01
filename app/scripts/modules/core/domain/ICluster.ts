import {ServerGroup} from './serverGroup';

export interface ICluster {
  account: string;
  cloudProvider: string;
  category: string;
  name: string;
  serverGroups: ServerGroup[];
}
