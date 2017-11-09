import * as React from 'react';

import {
  AccountCell, BasicCell, HrefCell, searchResultTypeRegistry, ISearchColumn,
  ISearchResult, HeaderCell, TableBody, TableHeader, TableRow, SearchResultTab,
} from 'core/search';

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

const cols: { [key: string]: ISearchColumn } = {
  LOADBALANCER: { key: 'loadBalancer', label: 'Name' },
  ACCOUNT: { key: 'account' },
  REGION: { key: 'region' },
  TYPE: { key: 'loadBalancerType', label: 'Type' },
};

const iconClass = 'fa fa-sitemap';
const displayName = 'Load Balancers';

const itemKeyFn = (item: ILoadBalancerSearchResult) =>
  [item.loadBalancer, item.account, item.region].join('|');
const itemSortFn = (a: ILoadBalancerSearchResult, b: ILoadBalancerSearchResult) => {
  const order: number = a.loadBalancer.localeCompare(b.loadBalancer);
  return order !== 0 ? order : a.region.localeCompare(b.region);
};

searchResultTypeRegistry.register({
  id: 'loadBalancers',
  iconClass,
  displayName,
  order: 5,

  displayFormatter: (searchResult: ILoadBalancerSearchResult, fromRoute: boolean) => {
    const name = fromRoute ? (searchResult as any).name : searchResult.loadBalancer;
    return `${name} (${searchResult.region})`;
  },

  components: {
    SearchResultTab: ({ ...props }) => (
      <SearchResultTab {...props} iconClass={iconClass} label={displayName} />
    ),

    SearchResultsHeader: () => (
      <TableHeader>
        <HeaderCell col={cols.LOADBALANCER}/>
        <HeaderCell col={cols.ACCOUNT}/>
        <HeaderCell col={cols.REGION}/>
        <HeaderCell col={cols.TYPE}/>
      </TableHeader>
    ),

    SearchResultsData: ({ results }) => (
      <TableBody>
        {results.slice().sort(itemSortFn).map(item => (
          <TableRow key={itemKeyFn(item)}>
            <HrefCell item={item} col={cols.LOADBALANCER} />
            <AccountCell item={item} col={cols.ACCOUNT} />
            <BasicCell item={item} col={cols.REGION} />
            <BasicCell item={item} col={cols.TYPE} />
          </TableRow>
        ))}
      </TableBody>
    )
  }
});
