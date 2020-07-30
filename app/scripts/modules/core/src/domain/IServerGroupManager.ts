import { IMoniker } from 'core/naming';
import { IEntityTags } from './IEntityTags';

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
