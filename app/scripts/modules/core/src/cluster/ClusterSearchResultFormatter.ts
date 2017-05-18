import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { ISearchResultFormatter } from '../search/searchResult/searchResultFormatter.registry';
import { ISearchResult } from '../search/search.service';
import { searchResultFormatterRegistry } from '../search/searchResult/searchResultFormatter.registry';

export interface IClusterSearchResult extends ISearchResult {
  cluster: string;
}

export class ClusterSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Clusters'; }
  public get order() { return 2; }
  public get icon() { return 'th'; }
  public displayFormatter(searchResult: IClusterSearchResult): IPromise<string> {
    return $q.when(searchResult.cluster);
  }
}

searchResultFormatterRegistry.register('clusters', new ClusterSearchResultFormatter());
