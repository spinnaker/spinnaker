import { IPromise } from 'angular';
import { Observable } from 'rxjs';
import { intersection, isUndefined } from 'lodash';

import { IQueryParams, IUrlBuilder, urlBuilderRegistry } from 'core/navigation';

import { searchResultTypeRegistry } from './searchResult/searchResultsType.registry';
import { ISearchResult } from './search.service';
import { ISearchResultType } from './searchResult/searchResultsType.registry';
import { ISearchResultSet } from './infrastructure/infrastructureSearch.service';
import { SearchFilterTypeRegistry } from './widgets/SearchFilterTypeRegistry';
import { SearchStatus } from './searchResult/SearchResults';

/**
 * External search registry entries add a section to the infrastructure search
 */
export interface IExternalSearchConfig {

  /**
   * The Search Result Type for this external search (registered in SearchResultTypeRegistry).
   */
  type: ISearchResultType;

  /**
   * Method to fetch search results
   * @param query
   */
  search: (query: string) => IPromise<ISearchResult[]>;

  /**
   * Class to build the URL for search results
   */
  urlBuilder: IUrlBuilder
}

export class ExternalSearchRegistry {
  private registry: IExternalSearchConfig[] = [];

  public getAll(): IExternalSearchConfig[] {
    return this.registry;
  }

  public register(searchConfig: IExternalSearchConfig) {
    const type = searchConfig.type;
    searchResultTypeRegistry.register(type);
    urlBuilderRegistry.register(type.id, searchConfig.urlBuilder);
    this.registry.push(searchConfig);
  }

  public search(queryParams: IQueryParams): Observable<ISearchResultSet> {
    const query = queryParams.q as string;

    // Returns true if the result object's values matches all the parameter values
    // Only accounts for parameters which are registered in SearchFilterTypeRegistry
    const matchesAllFilterKeys = (result: ISearchResult, params: IQueryParams) => {
      const filterKeys: string[] = intersection(SearchFilterTypeRegistry.getRegisteredFilterKeys(), Object.keys(params));
      return filterKeys.every(filterKey => {
        const resultVal: string = (result as any)[filterKey];
        return isUndefined(resultVal) || resultVal === params[filterKey];
      });
    };

    return Observable.from(this.registry).mergeMap(config => {
      const { type } = config;

      if (!queryParams.q) {
        return Observable.of({ type, results: [], status: SearchStatus.NO_RESULTS });
      }

      return Observable.fromPromise(config.search(query))
        // Perform additional client-side filtering of results after fetch completes
        .map(results => results.filter(result => matchesAllFilterKeys(result, queryParams)))
        .map(results => ({ type, results, status: SearchStatus.FINISHED }))
        .catch(error => {
          return Observable.of({ error, type, results: [], status: SearchStatus.ERROR });
        });
    });
  }
}

export const externalSearchRegistry = new ExternalSearchRegistry();
