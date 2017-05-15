import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { ISearchResultFormatter } from '../search/searchResult/searchResultFormatter.registry';
import { ISearchResult } from '../search/search.service';
import { searchResultFormatterRegistry } from '../search/searchResult/searchResultFormatter.registry';

export interface ILoadBalancerSearchResult extends ISearchResult {
  name?: string;
  loadBalancer: string;
  region: string;
}

export class LoadBalancerSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Load Balancers'; }
  public get order() { return 5; }
  public get iconClass() { return 'icon icon-elb'; }
  public displayFormatter(searchResult: ILoadBalancerSearchResult, fromRoute: boolean): IPromise<string> {
    const name = fromRoute ? searchResult.name : searchResult.loadBalancer;
    return $q.when(name + ' (' + searchResult.region + ')');
  }
}

searchResultFormatterRegistry.register('loadBalancers', new LoadBalancerSearchResultFormatter());
