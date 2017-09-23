import { ISearchResult } from 'core/search';

export interface IApplicationSearchResult extends ISearchResult {
  accounts: string[];
  application: string;
  cloudProviders: string;
  createTs: string;
  description: string;
  email: string;
  group: string;
  lastModifiedBy: string;
  legacyUdf: boolean;
  name: string;
  owner: string;
  pdApiKey: string;
  updateTs: string;
  url: string;
  user: string;
}
