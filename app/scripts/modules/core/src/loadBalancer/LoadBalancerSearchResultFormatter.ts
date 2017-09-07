import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { IResultRenderer, ISearchResult, ISearchResultFormatter, searchResultFormatterRegistry } from '../search';
import { LoadBalancerDisplayRenderer } from './LoadBalancerDisplayRenderer';

export interface ILoadBalancerSearchResult extends ISearchResult {
  name?: string;
  loadBalancer: string;
  region: string;
}

export class LoadBalancerSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Load Balancers'; }
  public get order() { return 5; }
  public get icon() { return 'sitemap'; }
  public displayFormatter(searchResult: ILoadBalancerSearchResult, fromRoute: boolean): IPromise<string> {
    const name = fromRoute ? searchResult.name : searchResult.loadBalancer;
    return $q.when(name + ' (' + searchResult.region + ')');
  }
  public get displayRenderer(): IResultRenderer {
    return LoadBalancerDisplayRenderer.renderer()
  }
}

searchResultFormatterRegistry.register('loadBalancers', new LoadBalancerSearchResultFormatter());
