import React from 'react';
import { BehaviorSubject, from as observableFrom, Observable, of as observableOf, Subject } from 'rxjs';
import { bufferCount, catchError, concatMap, filter, mergeMap, takeUntil } from 'rxjs/operators';

import { REST } from '../api';
import { IInstanceCounts, IServerGroup } from '../domain';
import {
  AccountCell,
  BasicCell,
  DefaultSearchResultTab,
  HeaderCell,
  HealthCountsCell,
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

import './serverGroup.less';

export interface IServerGroupSearchResult extends ISearchResult {
  account: string;
  application: string;
  cluster: string;
  detail: string;
  email?: string;
  region: string;
  sequence: string;
  serverGroup: string;
  stack: string;
  url: string;
  instanceCounts?: IInstanceCounts;
}

interface IServerGroupDataProps {
  resultSet: ISearchResultSet<IServerGroupSearchResult>;
}

interface IServerGroupDataState {
  resultSet: ISearchResultSet<IServerGroupSearchResult>;
}

interface IServerGroupTuple {
  toFetch: IServerGroupSearchResult;
  fetched: IServerGroup;
}

const makeServerGroupTuples = (sgToFetch: IServerGroupSearchResult[], fetched: IServerGroup[]): IServerGroupTuple[] => {
  const findFetchedValue = (toFetch: IServerGroupSearchResult) =>
    fetched.find(
      (sg) => sg.name === toFetch.serverGroup && sg.account === toFetch.account && sg.region === toFetch.region,
    );
  return sgToFetch.map((toFetch) => ({ toFetch, fetched: findFetchedValue(toFetch) }));
};

const fetchServerGroups = (toFetch: IServerGroupSearchResult[]): Observable<IServerGroupTuple[]> => {
  const fetchPromise = REST('/serverGroups')
    .query({ ids: toFetch.map((sg) => `${sg.account}:${sg.region}:${sg.serverGroup}`) })
    .get()
    .then((fetched: IServerGroup[]) => makeServerGroupTuples(toFetch, fetched));

  return observableFrom(fetchPromise);
};

/**
 * HOC which provides provides server group health/instance counts
 *
 * The /search endpoint does not return instance counts.
 * This component fetches instance counts separately and provides them to the wrapped component.
 * This component waits until it is rendered, then starts fetching instance counts.
 * It mutates the input search results and then passes the data to the nested component.
 */
const AddHealthCounts = (
  RawComponent: React.ComponentType<IServerGroupDataProps>,
): React.ComponentType<IServerGroupDataProps> => {
  return class FetchHealthCounts extends React.Component<IServerGroupDataProps, IServerGroupDataState> {
    public state = { serverGroups: [] } as any;
    private results$ = new BehaviorSubject<IServerGroupSearchResult[]>([]);
    private stop$ = new Subject();

    constructor(props: any) {
      super(props);

      // results to use when a fetch has failed.
      const failedFetch = (failedFetches: IServerGroupSearchResult[]) =>
        observableOf(failedFetches.map((toFetch) => ({ toFetch, fetched: { instanceCounts: null } as IServerGroup })));

      // fetch a batch of server groups.
      const processBatch = (batch: IServerGroupSearchResult[]): Observable<IServerGroupTuple[]> =>
        fetchServerGroups(batch).pipe(catchError(() => failedFetch(batch)));

      this.results$
        .pipe(
          mergeMap((searchResults: IServerGroupSearchResult[]) => {
            return observableFrom(searchResults).pipe(
              filter((result) => result.instanceCounts === undefined),
              // Serially fetch instance counts in batches of 25
              bufferCount(25),
              concatMap(processBatch),
            );
          }),
          takeUntil(this.stop$),
        )
        .subscribe((tuples: IServerGroupTuple[]) => {
          tuples.forEach((result) => (result.toFetch.instanceCounts = result.fetched.instanceCounts));
          const resultSet = { ...this.props.resultSet, results: this.results$.value.slice() };
          this.setState({ resultSet });
        });
    }

    private applyServerGroups(resultSet: ISearchResultSet<IServerGroupSearchResult>) {
      const itemSortFn = (a: IServerGroupSearchResult, b: IServerGroupSearchResult) => {
        const order = a.serverGroup.localeCompare(b.serverGroup);
        return order !== 0 ? order : a.region.localeCompare(b.region);
      };

      const serverGroups = resultSet.results.slice().sort(itemSortFn);
      this.results$.next(serverGroups);
      this.setState({ resultSet: { ...resultSet, results: serverGroups } });
    }

    public componentDidMount() {
      this.applyServerGroups(this.props.resultSet);
    }

    public componentWillReceiveProps(nextProps: IServerGroupDataProps) {
      this.applyServerGroups(nextProps.resultSet);
    }

    public componentWillUnmount() {
      this.stop$.next();
      this.stop$.complete();
    }

    public render() {
      return <RawComponent resultSet={this.props.resultSet} />;
    }
  };
};

class ServerGroupSearchResultType extends SearchResultType<IServerGroupSearchResult> {
  public id = 'serverGroups';
  public order = 3;
  public displayName = 'Server Groups';
  public iconClass = 'fa fa-th-large';

  private cols: { [key: string]: ISearchColumn } = {
    SERVERGROUP: { key: 'serverGroup', label: 'Name' },
    ACCOUNT: { key: 'account' },
    REGION: { key: 'region' },
    EMAIL: { key: 'email' },
    HEALTH: { key: 'instanceCounts', label: 'Health' },
  };

  public TabComponent = DefaultSearchResultTab;

  public HeaderComponent = () => (
    <SearchTableHeader>
      <HeaderCell col={this.cols.SERVERGROUP} />
      <HeaderCell col={this.cols.ACCOUNT} />
      <HeaderCell col={this.cols.REGION} />
      <HeaderCell col={this.cols.EMAIL} />
      <HeaderCell col={this.cols.HEALTH} />
    </SearchTableHeader>
  );

  private RawDataComponent = ({ resultSet }: { resultSet: ISearchResultSet<IServerGroupSearchResult> }) => {
    const itemKeyFn = (item: IServerGroupSearchResult) => [item.serverGroup, item.account, item.region].join('|');

    const results = resultSet.results.slice();

    return (
      <SearchTableBody>
        {results.map((item) => (
          <SearchTableRow key={itemKeyFn(item)}>
            <HrefCell item={item} col={this.cols.SERVERGROUP} />
            <AccountCell item={item} col={this.cols.ACCOUNT} />
            <BasicCell item={item} col={this.cols.REGION} />
            <BasicCell item={item} col={this.cols.EMAIL} />
            <HealthCountsCell item={item} col={this.cols.HEALTH} />
          </SearchTableRow>
        ))}
      </SearchTableBody>
    );
  };

  // tslint:disable-next-line:member-ordering
  public DataComponent = AddHealthCounts(this.RawDataComponent);

  public displayFormatter(searchResult: IServerGroupSearchResult) {
    return `${searchResult.serverGroup} (${searchResult.region})`;
  }
}

searchResultTypeRegistry.register(new ServerGroupSearchResultType());
