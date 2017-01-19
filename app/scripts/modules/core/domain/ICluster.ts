import {ServerGroup} from './serverGroup';

export interface ICluster {
  account: string;
  region: string;
  cloudProvider: string;
  category: string;
  name: string;
  serverGroups: ServerGroup[];
}
