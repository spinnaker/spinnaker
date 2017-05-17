import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { ISearchResultFormatter } from '../search/searchResult/searchResultFormatter.registry';
import { ISearchResult } from '../search/search.service';
import { searchResultFormatterRegistry } from '../search/searchResult/searchResultFormatter.registry';

export interface ISecurityGroupSearchResult extends ISearchResult {
  name: string;
  region: string;
  application: string;
}

export class SecurityGroupSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Security Groups'; }
  public get order() { return 6; }
  public get icon() { return 'exchange'; }
  public displayFormatter(searchResult: ISecurityGroupSearchResult): IPromise<string> {
    return $q.when(searchResult.name + ' (' + searchResult.region + ')');
  }
}

searchResultFormatterRegistry.register('securityGroups', new SecurityGroupSearchResultFormatter());
