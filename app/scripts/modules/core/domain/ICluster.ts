import {ServerGroup} from './serverGroup';

export interface ICluster {
  account: string;
  category: string;
  name: string;
  serverGroups: ServerGroup[];
}
