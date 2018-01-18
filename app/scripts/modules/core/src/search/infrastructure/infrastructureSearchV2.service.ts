import { ILogService, module } from 'angular';
import { intersection, isEmpty, isUndefined } from 'lodash';
import { Observable } from 'rxjs';

import { SETTINGS } from 'core/config';
import { UrlBuilderService, URL_BUILDER_SERVICE } from 'core/navigation';
import { IQueryParams } from 'core/navigation';

import { PostSearchResultSearcherRegistry } from '../searchResult/PostSearchResultSearcherRegistry';
import { SearchFilterTypeRegistry } from '../widgets/SearchFilterTypeRegistry';
import { externalSearchRegistry } from '../externalSearch.registry';
import { ISearchResultSet } from './infrastructureSearch.service';
import { ISearchResults } from '../search.service';
import { ISearchResultType } from '../searchResult/searchResultsType.registry';
import { SearchStatus } from '../searchResult/SearchResults';
import { IPostSearchResultSearcher } from '../searchResult/PostSearchResultSearcherRegistry';
import { searchResultTypeRegistry } from '../searchResult/searchResultsType.registry';
import { ISearchResult, SearchService, SEARCH_SERVICE } from '../search.service';
import { SearchResultHydratorRegistry } from '../searchResult/SearchResultHydratorRegistry';

const KEYWORD_PROP = SearchFilterTypeRegistry.KEYWORD_FILTER.key;

export class InfrastructureSearchServiceV2 {
  private EMPTY_RESULTS: ISearchResultSet[] = searchResultTypeRegistry.getAll()
    .map(type => ({ type, results: [], status: SearchStatus.FINISHED }));

  constructor(private $log: ILogService,
              private searchService: SearchService,
              private urlBuilderService: UrlBuilderService) {
    'ngInject';
  }

  /** Gets the SearchResultTypes that can be queried based on the current param values */
  private getInternalSearchResultTypesForParams(params: IQueryParams): ISearchResultType[] {
    const externalTypes: string[] = externalSearchRegistry.getAll().map(config => config.type.id);
    const postSearchResultKeys: string[] = PostSearchResultSearcherRegistry.getRegisteredTypes().map(mapping => mapping.sourceType);

    const paramKeys: string[] = Object.keys(params);

    const hasRequiredParams = (type: ISearchResultType) => {
      const requiredParams: string[] = type.requiredSearchFields || [];
      return requiredParams.every(field => paramKeys.includes(field));
    };

    return searchResultTypeRegistry.getAll()
      .filter(hasRequiredParams)
      .filter(type => !externalTypes.includes(type.id))
      .filter(type => params[KEYWORD_PROP] ? true : !postSearchResultKeys.includes(type.id));
  }

  /**
   * The search API uses `q` as the query parameter argument for a keyword search.
   * If a `key` search is being done, map it to `q`.
   * Add `cloudProvider`
   */
  private fixApiParams(apiParams: IQueryParams): IQueryParams {
    const { key, ...rest } = apiParams;
    const query = key ? { q: key } : {};
    return { ...rest, ...query, cloudProvider: SETTINGS.defaultProviders[0] };
  }

  private searchInternal(params: IQueryParams): Observable<ISearchResultSet> {
    // Fetch results for each type individually
    const searchTypesToQuery = this.getInternalSearchResultTypesForParams(params);

    return Observable.from(searchTypesToQuery)
      .mergeMap((type: ISearchResultType) => {
        const queryForType: IQueryParams = Object.assign({}, params, { type: type.id });

        return Observable.fromPromise(this.searchService.search(queryForType))
          .map((searchResults: ISearchResults<any>) => {
            return { type, results: searchResults.results, status: SearchStatus.FINISHED };
          })
          .catch((error: any) => {
            this.$log.warn(`Error fetching search results for type: ${type.id}`, error);
            return Observable.of({ error, type, results: [], status: SearchStatus.ERROR });
          });
      });
  }

  private searchExternal(params: IQueryParams): Observable<ISearchResultSet> {
    // Returns true if the result object's values matches all the parameter values
    // Only accounts for parameters which are registered in SearchFilterTypeRegistry
    const matchesAllFilterKeys = (result: ISearchResult) => {
      const filterKeys: string[] = intersection(SearchFilterTypeRegistry.getRegisteredFilterKeys(), Object.keys(params));
      return filterKeys.every(filterKey => {
        const resultVal: string = (result as any)[filterKey];
        return isUndefined(resultVal) || resultVal === params[filterKey];
      });
    };

    return externalSearchRegistry.search(params)
        .map((resultSet: ISearchResultSet) => {
          // perform additional client-side filtering of the external search results
          const filteredResults = resultSet.results.filter(matchesAllFilterKeys);
          return { ...resultSet, results: filteredResults };
        });
  }

  public search(apiParams: IQueryParams): Observable<ISearchResultSet> {
    if (isEmpty(apiParams)) {
      return Observable.from(this.EMPTY_RESULTS);
    }

    const params = this.fixApiParams(apiParams);

    const internalSearchResults$ = this.searchInternal(params);
    const externalSearchResults$ = this.searchExternal(params);

    // Calculate the result href and add to the object
    const applyResultHref = (result: ISearchResult) =>
      result.href = this.urlBuilderService.buildFromMetadata(result);

    return internalSearchResults$.merge(externalSearchResults$)
      // Derive search results for some type when data from another type arrives.
      // i.e., clusters are derived from serverGroups
      .mergeMap((resultSet: ISearchResultSet) => {
        const mappings = PostSearchResultSearcherRegistry.getRegisteredTypes();
        const derivedSearchers: IPostSearchResultSearcher[] = mappings
          .filter(mapping => mapping.targetType === resultSet.type.id)
          .map(mapping => PostSearchResultSearcherRegistry.getPostResultSearcher(mapping.sourceType));

        const derivedSearches$ = Observable.from(derivedSearchers)
          .mergeMap(searcher => searcher.getPostSearchResults(resultSet));

        return Observable.of(resultSet).merge(derivedSearches$)
      })
      .do((result: ISearchResultSet) => {
        // Add the links
        result.results.forEach(applyResultHref);

        // Do hydration (post processing, I guess?) on each type that has a hydrator registered
        const hydrator = SearchResultHydratorRegistry.getSearchResultHydrator(result.type.id);
        if (hydrator) {
          hydrator.hydrate(result.results);
        }
      });
  }
}

export const INFRASTRUCTURE_SEARCH_SERVICE_V2 = 'spinnaker.core.infrastructure.search.service.v2';
module(INFRASTRUCTURE_SEARCH_SERVICE_V2, [
  SEARCH_SERVICE,
  URL_BUILDER_SERVICE
]).service('infrastructureSearchServiceV2', InfrastructureSearchServiceV2);
