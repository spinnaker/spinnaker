import { ISearchResult } from 'core/search';

export interface IClusterSearchResult extends ISearchResult {
  account: string;
  application: string;
  cluster: string;
  email?: string;
  stack: string;
}
