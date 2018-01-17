import { get, intersection, isEmpty, isUndefined } from 'lodash';
import { IPromise, IQService, module } from 'angular';

import { SETTINGS } from 'core/config/settings';
import { UrlBuilderService, URL_BUILDER_SERVICE } from 'core/navigation/urlBuilder.service';
import { IQueryParams } from 'core/navigation/urlParser';
import { externalSearchRegistry } from 'core/search/externalSearch.registry';
import {
  getFallbackResults,
  ISearchResult,
  ISearchResults,
  SearchService,
  SEARCH_SERVICE
} from 'core/search/search.service';
import { searchResultTypeRegistry } from 'core/search/searchResult/searchResultsType.registry';
import {
  ITypeMapping,
  PostSearchResultSearcherRegistry
} from 'core/search/searchResult/PostSearchResultSearcherRegistry';
import { SearchFilterTypeRegistry } from 'core/search/widgets/SearchFilterTypeRegistry';
import { ISearchResultSet } from './infrastructureSearch.service';

export class InfrastructureSearchServiceV2 {

  private static EMPTY_RESULT: ISearchResultSet[] = [{
    id: '',
    category: '',
    iconClass: '',
    order: 0,
    results: getFallbackResults().results
  }];

  constructor(private $q: IQService,
              private searchService: SearchService,
              private urlBuilderService: UrlBuilderService) {
    'ngInject';
  }

  public search(params: IQueryParams): IPromise<ISearchResultSet[]> {
    if (isEmpty(params)) {
      return this.$q.when(InfrastructureSearchServiceV2.EMPTY_RESULT);
    }

    // retrieve a list of all the types we should be searching and remove any we should not be
    const paramKeys: Set<string> = new Set<string>(Object.keys(params));
    const postSearchResultKeys: Set<string> = new Set<string>(PostSearchResultSearcherRegistry.getRegisteredTypes()
      .map((mapping: ITypeMapping) => mapping.sourceType));
    const types: string[] = searchResultTypeRegistry.getSearchCategories()
      .filter((category: string) => {

        // check the search result formatter to ensure that if it requires a search field to be present,
        // that it is or remove it from the types to be searched.
        // e.g., do not search for instances if a keyword is not specified.
        const requiredFields: string[] = searchResultTypeRegistry.get(category).requiredSearchFields;
        return !(requiredFields && !requiredFields.some((field: string) => paramKeys.has(field)));
      })
      .filter((category: string) => params[SearchFilterTypeRegistry.KEYWORD_FILTER.key] ? true : !postSearchResultKeys.has(category));

    // the search API uses `q` as the query parameter argument for a keyword search so if
    // a keyword search is being done, map it to `q` and remove the keyword param.
    const KEYWORD_FILTER_KEY = SearchFilterTypeRegistry.KEYWORD_FILTER.key;
    const keyword = params[KEYWORD_FILTER_KEY];
    delete params[KEYWORD_FILTER_KEY];
    params.q = keyword;

    // add the cloudprovider(s).
    params.cloudProvider = SETTINGS.defaultProviders[0];

    // perform searches by type separately for performance reasons
    const promises: IPromise<ISearchResults<ISearchResult>>[] = [];
    types.forEach((type: string) => {
      const copy: IQueryParams = Object.assign({}, params, { type });
      promises.push(this.searchService.search(copy));
    });

    return this.$q.all(promises).then((results: ISearchResults<ISearchResult>[]) => {
      // reduce results to map of category <-> ISearchResultSet
      const searchResults: ISearchResultSet[] = results.map((result: ISearchResults<ISearchResult>, index: number) => {
        result.query = types[index];
        result.results.forEach((searchResult: ISearchResult) => {
          searchResult.href = this.urlBuilderService.buildFromMetadata(searchResult);
        });

        return result;
      }).map((result: ISearchResults<ISearchResult>) => this.buildSearchResultSet(result.query, result.query, result.results));

      // right now, only keyword searches are supported for external search registries
      if (keyword) {
        return externalSearchRegistry.search(get(params, 'q'))
          .then((externalResults: ISearchResult[]) => {

            const activeFilters: string[] = intersection(SearchFilterTypeRegistry.getRegisteredFilterKeys(), Object.keys(params));
            const filteredExternalResults: ISearchResult[] = externalResults.filter((externalSearchResult: ISearchResult) => {
              return activeFilters.every((filter: string) => {
                const filterVal: string = get(externalSearchResult, filter);
                return isUndefined(filterVal) || filterVal === get(params, filter)
              });
            });

            // map the results by type <-> list of results
            const externalResultMap: { [key: string]: ISearchResult[] } =
              filteredExternalResults.reduce((resultMap: { [key: string]: ISearchResult[] }, result: ISearchResult) => {
                let items: ISearchResult[] = resultMap[result.type];
                if (!items) {
                  items = resultMap[result.type] = [];
                }
                result.href = this.urlBuilderService.buildFromMetadata(result);
                items.push(result);

                return resultMap;
              }, {});

            // populate and return the aggregated search results
            Object.keys(externalResultMap).forEach((externalResultType: string) => {
              const existingResultSet: ISearchResultSet =
                searchResults.find((resultSet: ISearchResultSet) => resultSet.id === externalResultType);
              if (existingResultSet) {
                existingResultSet.results = externalResultMap[externalResultType];
              } else {
                const resultSet: ISearchResultSet = this.buildSearchResultSet(externalResultType, externalResultType, externalResultMap[externalResultType]);
                if (resultSet) {
                  searchResults.push(resultSet);
                }
              }
            });

            return searchResults;
          });
      } else {
        return searchResults;
      }
    });
  }

  private buildSearchResultSet(id: string, type: string, results: ISearchResult[]): ISearchResultSet {

    let result: ISearchResultSet = null;
    const config = searchResultTypeRegistry.get(type);
    if (config) {
      result = {
        id,
        category: config.displayName,
        iconClass: config.iconClass,
        order: config.order,
        results
      };
    }

    return result;
  }
}

export const INFRASTRUCTURE_SEARCH_SERVICE_V2 = 'spinnaker.core.infrastructure.search.service.v2';
module(INFRASTRUCTURE_SEARCH_SERVICE_V2, [
  SEARCH_SERVICE,
  URL_BUILDER_SERVICE
]).service('infrastructureSearchServiceV2', InfrastructureSearchServiceV2);
