import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { IResultRenderer, ISearchResult, ISearchResultFormatter, searchResultFormatterRegistry } from '../search';
import { ClusterDisplayRenderer } from 'core/cluster/ClusterDisplayRenderer';

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
  public get displayRenderer(): IResultRenderer {
    return ClusterDisplayRenderer.renderer()
  }
}

searchResultFormatterRegistry.register('clusters', new ClusterSearchResultFormatter());
