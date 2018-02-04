import * as React from 'react';
import { Observable } from 'rxjs';

import { ReactInjector } from 'core/reactShims';

import { ISearchParams, ISearchResult, ISearchResults } from '../search.service';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { DefaultSearchResultTab } from './DefaultSearchResultTab';

export interface ISearchResultSetProps<T extends ISearchResult> {
  resultSet: ISearchResultSet<T>;
}

export interface ISearchResultTabProps<T extends ISearchResult> {
  resultSet: ISearchResultSet<T>;
  isActive: boolean;
}

export abstract class SearchResultType<T extends ISearchResult = ISearchResult> {
  /** The unique key for the type, i.e., 'applications', 'serverGroup' */
  public abstract id: string;

  /** search fields necessary when searching for this type, i.e., ['key'] for instance search */
  public fieldsRequired: string[] = [];

  /** The order to appear in the search result tabs */
  public abstract order: number;
  /** The display name in the search result tabs */
  public abstract displayName: string;
  /** The icon class in the search result tabs */
  public abstract iconClass: string;

  // These components should render the results (Tab, Header, and Data)
  public TabComponent:    React.ComponentType<ISearchResultTabProps<T>> = DefaultSearchResultTab;
  public HeaderComponent: React.ComponentType<ISearchResultSetProps<T>>;
  public DataComponent:   React.ComponentType<ISearchResultSetProps<T>>;

  // v1 search field
  public hideIfEmpty?: boolean;

  // v1 search field
  public displayFormatter?(entry: ISearchResult, fromRoute?: boolean): string;

  /** Override this method as necessary */
  public search(params: ISearchParams, _otherResults?: Observable<ISearchResultSet>): Observable<ISearchResults<T>> {
    const searchService = ReactInjector.searchService;
    const { key, ...otherParams } = params;
    const searchParams = { ...otherParams, q: key, type: this.id };
    return Observable.fromPromise(searchService.search(searchParams));
  }
}
