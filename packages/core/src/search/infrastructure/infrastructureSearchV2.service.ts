import { isEmpty } from 'lodash';
import { uniqBy } from 'lodash';
import { from as observableFrom, Observable, of as observableOf, Subject } from 'rxjs';
import { catchError, finalize, map, mergeMap, tap } from 'rxjs/operators';

import { ISearchResultSet } from './infrastructureSearch.service';
import { IQueryParams, UrlBuilder } from '../../navigation';
import { ISearchResult, ISearchResults } from '../search.service';
import { SearchStatus } from '../searchResult/SearchResults';
import { SearchResultType } from '../searchResult/searchResultType';
import { searchResultTypeRegistry } from '../searchResult/searchResultType.registry';

export class InfrastructureSearchServiceV2 {
  private static EMPTY_RESULTS: ISearchResultSet[] = searchResultTypeRegistry
    .getAll()
    .map((type) => ({ type, results: [], status: SearchStatus.FINISHED }));

  public static search(apiParams: IQueryParams): Observable<ISearchResultSet> {
    if (isEmpty(apiParams)) {
      return observableFrom(this.EMPTY_RESULTS);
    }

    const params = { ...apiParams };
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
      // Add URLs to each search result (discard duplicate results)
      const results = uniqBy(
        searchResults.results.map((result) => addComputedAttributes(result, type)),
        (r) => r.href,
      );
      const query: string = apiParams.key as string;
      return { type, results, status: SearchStatus.FINISHED, query };
    };

    const emitErrorResultSet = (error: any, type: SearchResultType): Observable<ISearchResultSet> => {
      return observableOf({ error, type, results: [], status: SearchStatus.ERROR });
    };

    return observableFrom(types).pipe(
      mergeMap((type: SearchResultType) => {
        return type.search(params, otherResults$).pipe(
          map((searchResults: ISearchResults<any>) => makeResultSet(searchResults, type)),
          catchError((error: any) => emitErrorResultSet(error, type)),
        );
      }),
      tap((result: ISearchResultSet<any>) => otherResults$.next(result)),
      finalize(() => otherResults$.complete()),
    );
  }
}
