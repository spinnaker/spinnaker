import React from 'react';

import {
  AccountCell,
  BasicCell,
  DefaultSearchResultTab,
  HeaderCell,
  HrefCell,
  ISearchColumn,
  ISearchResult,
  ISearchResultSet,
  SearchResultType,
  searchResultTypeRegistry,
  SearchTableBody,
  SearchTableHeader,
  SearchTableRow,
} from '../search';

export interface ILoadBalancerSearchResult extends ISearchResult {
  account: string;
  application: string;
  detail: string;
  displayName: string;
  href: string;
  loadBalancer: string;
  loadBalancerType: string;
  provider: string;
  region: string;
  stack: string;
  type: string;
  url: string;
  vpcId: string;
}

class LoadBalancersSearchResultType extends SearchResultType<ILoadBalancerSearchResult> {
  public id = 'loadBalancers';
  public order = 5;
  public displayName = 'Load Balancers';
  public iconClass = 'fa icon-sitemap';

  private cols: { [key: string]: ISearchColumn } = {
    LOADBALANCER: { key: 'loadBalancer', label: 'Name' },
    ACCOUNT: { key: 'account' },
    REGION: { key: 'region' },
    TYPE: { key: 'loadBalancerType', label: 'Type' },
  };

  public TabComponent = DefaultSearchResultTab;

  public HeaderComponent = () => (
    <SearchTableHeader>
      <HeaderCell col={this.cols.LOADBALANCER} />
      <HeaderCell col={this.cols.ACCOUNT} />
      <HeaderCell col={this.cols.REGION} />
      <HeaderCell col={this.cols.TYPE} />
    </SearchTableHeader>
  );

  public DataComponent = ({ resultSet }: { resultSet: ISearchResultSet<ILoadBalancerSearchResult> }) => {
    const itemKeyFn = (item: ILoadBalancerSearchResult) => [item.loadBalancer, item.account, item.region].join('|');
    const itemSortFn = (a: ILoadBalancerSearchResult, b: ILoadBalancerSearchResult) => {
      const order: number = a.loadBalancer.localeCompare(b.loadBalancer);
      return order !== 0 ? order : a.region.localeCompare(b.region);
    };

    const results = resultSet.results.slice().sort(itemSortFn);

    return (
      <SearchTableBody>
        {results
          .slice()
          .sort(itemSortFn)
          .map((item) => (
            <SearchTableRow key={itemKeyFn(item)}>
              <HrefCell item={item} col={this.cols.LOADBALANCER} />
              <AccountCell item={item} col={this.cols.ACCOUNT} />
              <BasicCell item={item} col={this.cols.REGION} />
              <BasicCell item={item} col={this.cols.TYPE} />
            </SearchTableRow>
          ))}
      </SearchTableBody>
    );
  };

  public displayFormatter(searchResult: ILoadBalancerSearchResult, fromRoute: boolean) {
    const name = fromRoute ? (searchResult as any).name : searchResult.loadBalancer;
    return `${name} (${searchResult.region})`;
  }
}

searchResultTypeRegistry.register(new LoadBalancersSearchResultType());
