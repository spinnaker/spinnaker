import * as React from 'react';

import {
  AccountCell, BasicCell, HrefCell, searchResultTypeRegistry,
  SearchResultsHeaderComponent, SearchResultsDataComponent, DefaultSearchResultTab,
  ISearchResultType, ISearchResult, HeaderCell, TableBody, TableHeader, TableRow, ISearchColumn,
} from 'core/search';

export interface IInstanceSearchResult extends ISearchResult {
  account: string;
  application: string;
  cluster: string;
  displayName: string;
  href: string;
  instanceId: string;
  provider: string;
  region: string;
  serverGroup: string;
  type: string;
}

const cols: { [key: string]: ISearchColumn } = {
  INSTANCE: { key: 'instanceId', label: 'Instance ID' },
  ACCOUNT: { key: 'accounts' },
  REGION: { key: 'region' },
  SERVERGROUP: { key: 'serverGroup' }
};

const iconClass = 'fa fa-hdd-o';
const displayName = 'Instances';

const itemKeyFn = (item: IInstanceSearchResult) => item.instanceId;
const itemSortFn = (a: IInstanceSearchResult, b: IInstanceSearchResult) =>
  a.instanceId.localeCompare(b.instanceId);

const SearchResultsHeader: SearchResultsHeaderComponent = () => (
  <TableHeader>
    <HeaderCell col={cols.INSTANCE}/>
    <HeaderCell col={cols.ACCOUNT}/>
    <HeaderCell col={cols.REGION}/>
    <HeaderCell col={cols.SERVERGROUP}/>
  </TableHeader>
);

const SearchResultsData: SearchResultsDataComponent = ({ results }) => (
  <TableBody>
    {results.slice().sort(itemSortFn).map(item => (
      <TableRow key={itemKeyFn(item)}>
        <HrefCell item={item} col={cols.INSTANCE} />
        <AccountCell item={item} col={cols.ACCOUNT} />
        <BasicCell item={item} col={cols.REGION} />
        <BasicCell item={item} col={cols.SERVERGROUP} defaultValue="Standalone Instance" />
      </TableRow>
    ))}
  </TableBody>
);

const instancesSearchResultType: ISearchResultType = {
  id: 'instances',
  order: 4,
  iconClass,
  displayName,

  displayFormatter: (searchResult: IInstanceSearchResult) => {
    const serverGroup = searchResult.serverGroup || 'standalone instance';
    return `${searchResult.instanceId} (${serverGroup} - ${searchResult.region})`;
  },
  components: {
    SearchResultTab: DefaultSearchResultTab,
    SearchResultsHeader,
    SearchResultsData,
  },
};

searchResultTypeRegistry.register(instancesSearchResultType);
