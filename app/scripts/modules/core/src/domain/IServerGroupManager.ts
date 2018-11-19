import { IMoniker } from 'core/naming';
import { IEntityTags } from 'core/domain';

export interface IServerGroupManager {
  account: string;
  cloudProvider: string;
  displayName?: string;
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
