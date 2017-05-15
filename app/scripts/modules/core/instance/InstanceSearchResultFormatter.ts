import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { ISearchResultFormatter } from '../search/searchResult/searchResultFormatter.registry';
import { ISearchResult } from '../search/search.service';
import { searchResultFormatterRegistry } from '../search/searchResult/searchResultFormatter.registry';

export interface IInstanceSearchResult extends ISearchResult {
  serverGroup?: string;
  instanceId: string;
  region: string;
}

export class InstanceSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Instances'; }
  public get order() { return 4; }
  public get icon() { return 'hdd-o'; }
  public displayFormatter(searchResult: IInstanceSearchResult): IPromise<string> {
    const serverGroup = searchResult.serverGroup || 'standalone instance';
    return $q.when(searchResult.instanceId + ' (' + serverGroup + ' - ' + searchResult.region + ')');
  }
}

searchResultFormatterRegistry.register('instances', new InstanceSearchResultFormatter());
