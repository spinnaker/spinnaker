import { IMoniker } from 'core/naming';

export interface IServerGroupManager {
  account: string;
  cloudProvider: string;
  moniker: IMoniker;
  name: string;
  region: string;
  serverGroups: IServerGroupSummary[];
}

export interface IServerGroupSummary {
  account: string;
  moniker: IMoniker;
  name: string;
  region: string;
}
