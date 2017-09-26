import { IServerGroup } from 'core/domain';
import { ISearchResult } from 'core/search';

export interface IClusterSearchResult extends ISearchResult {
  account: string;
  application: string;
  cluster: string;
  email?: string;
  serverGroups?: IServerGroup[];
  stack: string;
}
