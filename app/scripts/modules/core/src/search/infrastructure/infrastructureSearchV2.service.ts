import { IPromise, IQService, module } from 'angular';
import { get, intersection, isEmpty, isUndefined, groupBy, flatten } from 'lodash';

import { SETTINGS } from 'core/config/settings';
import { UrlBuilderService, URL_BUILDER_SERVICE } from 'core/navigation/urlBuilder.service';
import { IQueryParams } from 'core/navigation/urlParser';
import { externalSearchRegistry } from 'core/search/externalSearch.registry';
import { getFallbackResults, ISearchResult, SearchService, SEARCH_SERVICE } from 'core/search/search.service';
import { searchResultTypeRegistry } from 'core/search/searchResult/searchResultsType.registry';
import { PostSearchResultSearcherRegistry } from 'core/search/searchResult/PostSearchResultSearcherRegistry';
import { SearchFilterTypeRegistry } from 'core/search/widgets/SearchFilterTypeRegistry';
import { ISearchResultSet } from './infrastructureSearch.service';

const KEYWORD_PROP = SearchFilterTypeRegistry.KEYWORD_FILTER.key;

export class InfrastructureSearchServiceV2 {
  constructor(private $q: IQService,
              private searchService: SearchService,
              private urlBuilderService: UrlBuilderService) {
    'ngInject';
  }

  // Tests that all required fields are present for a search result type
  private requiredSearchFieldsExist(paramKeys: string[], category: string) {
    const requiredFields: string[] = searchResultTypeRegistry.get(category).requiredSearchFields;
    return !requiredFields || requiredFields.every(field => paramKeys.includes(field));
  }

  private emptyResults(): ISearchResultSet[] {
    const type = searchResultTypeRegistry.get('applications');
    const results = getFallbackResults().results;
    return [{ type, results }];
  }

  private buildSearchResultSet(typeId: string, results: ISearchResult[]): ISearchResultSet {
    const type = searchResultTypeRegistry.get(typeId);
    return type ? { type, results } : null;
  }

  public search(params: IQueryParams): IPromise<ISearchResultSet[]> {
    if (isEmpty(params)) {
      return this.$q.when(this.emptyResults());
    }

    // retrieve a list of all the types we should be searching and remove any we should not be
    const paramKeys = Object.keys(params);
    const postSearchResultKeys = PostSearchResultSearcherRegistry.getRegisteredTypes().map(mapping => mapping.sourceType);

    const types: string[] = searchResultTypeRegistry.getSearchCategories()
      .filter(category => this.requiredSearchFieldsExist(paramKeys, category))
      .filter(category => params[KEYWORD_PROP] ? true : !postSearchResultKeys.includes(category));

    // the search API uses `q` as the query parameter argument for a keyword search so if
    // a keyword search is being done, map it to `q` and remove the keyword param.
    const KEYWORD_FILTER_KEY = SearchFilterTypeRegistry.KEYWORD_FILTER.key;
    const keyword = params[KEYWORD_FILTER_KEY];
    delete params[KEYWORD_FILTER_KEY];
    params.q = keyword;

    // add the cloudprovider(s).
    params.cloudProvider = SETTINGS.defaultProviders[0];

    // perform searches by type separately for performance reasons
    const searchByTypePromises: IPromise<ISearchResultSet>[] = types.map((type: string) => {
      return this.searchService.search(Object.assign({}, params, { type }))
        .then(searchResults => this.buildSearchResultSet(type, searchResults.results));
    });

    // Calculate the result href and add to the object
    const applyResultHref = (result: ISearchResult) =>
      result.href = this.urlBuilderService.buildFromMetadata(result);

    return this.$q.all(searchByTypePromises).then((searchResultSets: ISearchResultSet[]) => {
      flatten(searchResultSets.map(set => set.results)).forEach(applyResultHref);

      // right now, only keyword searches are supported for external search registries
      if (!keyword) {
        return searchResultSets;
      }

      return externalSearchRegistry.search(params.q as string)
        .then((externalResults: ISearchResult[]) => {
          const activeFilters: string[] = intersection(SearchFilterTypeRegistry.getRegisteredFilterKeys(), Object.keys(params));
          const filteredExternalResults: ISearchResult[] = externalResults
            .filter((externalSearchResult: ISearchResult) => {
              return activeFilters.every((filter: string) => {
                const filterVal: string = get(externalSearchResult, filter);
                return isUndefined(filterVal) || filterVal === get(params, filter);
              });
            });
          filteredExternalResults.forEach(applyResultHref);

          const externalResultsByType: { [key: string]: ISearchResult[] } = groupBy(filteredExternalResults, 'type');
          const externalResultSets: ISearchResultSet[] = Object.keys(externalResultsByType)
            .map(type => this.buildSearchResultSet(type, externalResultsByType[type]));
          const externalSearchTypes = externalResultSets.map(set => set.type);

          // Merge the external results with the normal results
          return searchResultSets.filter(resultSet => !externalSearchTypes.includes(resultSet.type)).concat(externalResultSets);
        });
    });
  }
}

export const INFRASTRUCTURE_SEARCH_SERVICE_V2 = 'spinnaker.core.infrastructure.search.service.v2';
module(INFRASTRUCTURE_SEARCH_SERVICE_V2, [
  SEARCH_SERVICE,
  URL_BUILDER_SERVICE
]).service('infrastructureSearchServiceV2', InfrastructureSearchServiceV2);
