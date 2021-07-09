import { IEntityTags } from './IEntityTags';
import { IMoniker } from '../naming';

export interface IServerGroupManager {
  account: string;
  cloudProvider: string;
  entityTags?: IEntityTags;
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
