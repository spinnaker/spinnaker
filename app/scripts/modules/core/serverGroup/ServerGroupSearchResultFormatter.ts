import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { ISearchResultFormatter } from '../search/searchResult/searchResultFormatter.registry';
import { ISearchResult } from '../search/search.service';
import { searchResultFormatterRegistry } from '../search/searchResult/searchResultFormatter.registry';

export interface IServerGroupSearchResult extends ISearchResult {
  serverGroup: string;
  region: string;
}

export class ServerGroupSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Server Groups'; }
  public get order() { return 6; }
  public get icon() { return 'th-large'; }
  public displayFormatter(searchResult: IServerGroupSearchResult): IPromise<string> {
    return $q.when(searchResult.serverGroup + ' (' + searchResult.region + ')');
  }
}

searchResultFormatterRegistry.register('serverGroups', new ServerGroupSearchResultFormatter());
