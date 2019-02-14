import { isEmpty } from 'lodash';
import { Observable, Subject } from 'rxjs';

import { SETTINGS } from 'core/config';
import { UrlBuilder, IQueryParams } from 'core/navigation';

import { ISearchResultSet } from './infrastructureSearch.service';
import { ISearchResult, ISearchResults } from '../search.service';
import { SearchResultType } from '../searchResult/searchResultType';
import { SearchStatus } from '../searchResult/SearchResults';
import { searchResultTypeRegistry } from '../searchResult/searchResultType.registry';

export class InfrastructureSearchServiceV2 {
  private static EMPTY_RESULTS: ISearchResultSet[] = searchResultTypeRegistry
    .getAll()
    .map(type => ({ type, results: [], status: SearchStatus.FINISHED }));

  public static search(apiParams: IQueryParams): Observable<ISearchResultSet> {
    if (isEmpty(apiParams)) {
      return Observable.from(this.EMPTY_RESULTS);
    }

    const params = { ...apiParams };
    if (SETTINGS.defaultProviders && SETTINGS.defaultProviders.length > 0) {
      params.cloudProvider = SETTINGS.defaultProviders[0];
    }
    const types = searchResultTypeRegistry.getAll();
    const otherResults$ = new Subject<ISearchResultSet>();

    /** Add the href and displayName attributes */
    const addComputedAttributes = (result: ISearchResult, type: SearchResultType): ISearchResult => {
      return {
        ...result,
        href: UrlBuilder.buildFromMetadata(result),
        displayName: type.displayFormatter(result),
      };
    };

    const makeResultSet = (searchResults: ISearchResults<any>, type: SearchResultType): ISearchResultSet => {
      // Add URLs to each search result
      const results = searchResults.results.map(result => addComputedAttributes(result, type));
      const query: string = apiParams.key as string;
      return { type, results, status: SearchStatus.FINISHED, query };
    };

    const emitErrorResultSet = (error: any, type: SearchResultType): Observable<ISearchResultSet> => {
      return Observable.of({ error, type, results: [], status: SearchStatus.ERROR });
    };

    return Observable.from(types)
      .mergeMap((type: SearchResultType) => {
        return type
          .search(params, otherResults$)
          .map((searchResults: ISearchResults<any>) => makeResultSet(searchResults, type))
          .catch((error: any) => emitErrorResultSet(error, type));
      })
      .do((result: ISearchResultSet<any>) => otherResults$.next(result))
      .finally(() => otherResults$.complete());
  }
}
