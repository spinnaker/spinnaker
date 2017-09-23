import { flatten, isEmpty, isObject, isString } from 'lodash';
import { module, IDeferred, IPromise, IQService } from 'angular';
import { Observable, Subject } from 'rxjs';

import { SETTINGS } from 'core/config/settings';
import { IQueryParams } from 'core/navigation';
import { SearchFilterTypeRegistry } from 'core/search/widgets/SearchFilterTypeRegistry';
import { UrlBuilderService, URL_BUILDER_SERVICE } from 'core/navigation/urlBuilder.service';
import { ProviderServiceDelegate, PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';
import {
  getFallbackResults,
  ISearchParams,
  ISearchResult,
  ISearchResults,
  SearchService,
  SEARCH_SERVICE
} from '../search.service';
import {
  IResultDisplayFormatter,
  ISearchResultFormatter,
  searchResultFormatterRegistry
} from '../searchResult/searchResultFormatter.registry';
import { externalSearchRegistry } from '../externalSearch.registry';
import { ITypeMapping, PostSearchResultSearcherRegistry } from 'core/search/searchResult/PostSearchResultSearcherRegistry';

export interface ISearchResultSet {
  id: string,
  category: string,
  icon: string,
  iconClass: string,
  order: number,
  results: ISearchResult[]
}

export interface IProviderResultFormatter {
  [category: string]: IResultDisplayFormatter,
}

export class InfrastructureSearcher {

  private deferred: IDeferred<ISearchResultSet[]>;
  public querySubject: Subject<string | IQueryParams> = new Subject<string | IQueryParams>();

  constructor(private $q: IQService, private providerServiceDelegate: ProviderServiceDelegate, searchService: SearchService, urlBuilderService: UrlBuilderService) {
    'ngInject'
    this.querySubject.switchMap(
      (query: string | IQueryParams) => {

        if (!query || (isString(query) && (query.trim() === '') || (isObject(query) && isEmpty(query)))) {
          return Observable.of(getFallbackResults());
        }

        let searchParams: ISearchParams;

        // if the query is a string, then it's the legacy search page so do what that did before
        if (isString(query)) {
          searchParams = { q: query, type: searchResultFormatterRegistry.getSearchCategories() };
        } else {

          // otherwise, it's the new search so we need to do a bunch of things differently
          // for new search we don't need to search applications and clusters since those are derivable
          // from server groups.  the search API now intelligently tries to guess a query string from the
          // search parameters passed to the search API.
          // this bit of code removes any registered post search results searchers from the `type` query
          // parameter sent to the search API because we are going to get that data later (further below)
          const postSearchResultKeys: Set<string> =
            new Set<string>(PostSearchResultSearcherRegistry.getRegisteredTypes()
              .map((mapping: ITypeMapping) => mapping.sourceType));
          searchParams = {
            type: searchResultFormatterRegistry.getSearchCategories().filter((category: string) => query[SearchFilterTypeRegistry.KEYWORD_FILTER.key] ? true : !postSearchResultKeys.has(category))
          };

          // the search API uses `q` as the query parameter argument for a keyword search so if a keyword
          // search is being done, map it to `q`
          const copy = Object.assign({}, query);
          copy.cloudProvider = SETTINGS.defaultProviders[0];
          if (copy[SearchFilterTypeRegistry.KEYWORD_FILTER.key]) {
            searchParams.q = <string>copy[SearchFilterTypeRegistry.KEYWORD_FILTER.key];
            delete copy[SearchFilterTypeRegistry.KEYWORD_FILTER.key];
          }
          Object.assign(searchParams, copy);
        }

        return Observable.zip(
          searchService.search(searchParams),
          externalSearchRegistry.search(query),
          (s1, s2) => {
            s1.results = s1.results.concat(s2);
            return s1;
          }
        )
      })
      .subscribe((result: ISearchResults<ISearchResult>) => {

        const categorizedSearchResults: { [type: string]: ISearchResult[] } =
          result.results.reduce((categories: { [type: string]: ISearchResult[] }, entry: ISearchResult) => {
            this.formatResult(entry.type, entry).then((name) => entry.displayName = name);
            entry.href = urlBuilderService.buildFromMetadata(entry);
            if (!categories[entry.type]) {
              categories[entry.type] = [];
            }
            categories[entry.type].push(entry);
            return categories;
          }, {});

        const results: ISearchResultSet[] = Object.keys(categorizedSearchResults)
          .filter(c => searchResultFormatterRegistry.get(c))
          .map(category => {
            const config = searchResultFormatterRegistry.get(category);
            return {
              id: category,
              category: config.displayName,
              icon: config.icon,
              iconClass: config.iconClass,
              order: config.order,
              hideIfEmpty: config.hideIfEmpty,
              results: categorizedSearchResults[category]
            };
          });

        // finally, for any registered post search result searcher, take its registered type mapping,
        // retrieve that data from the search results from the search API above, and pass to the
        // appropriate post search result searcher.
        // the post search result searcher will return a promise containing the data and we want all of
        // the promises to resolve before we return to the search controller so we `$q.all` it.
        const promises: IPromise<ISearchResultSet[]>[] = [this.$q.when(results)];
        PostSearchResultSearcherRegistry.getRegisteredTypes().forEach((mapping: ITypeMapping) => {
          if (!categorizedSearchResults[mapping.sourceType] && !isEmpty(categorizedSearchResults)) {
            promises.push(PostSearchResultSearcherRegistry.getPostResultSearcher(mapping.sourceType).getPostSearchResults(categorizedSearchResults[mapping.targetType]));
          }
        });

        this.$q.all(promises).then((promiseResults) => this.deferred.resolve(flatten(promiseResults)));
      });
  }

  public query(q: string | IQueryParams): IPromise<ISearchResultSet[]> {
    this.deferred = this.$q.defer();
    this.querySubject.next(q);
    return this.deferred.promise;
  }

  public getCategoryConfig(category: string): ISearchResultFormatter {
    return searchResultFormatterRegistry.get(category);
  }

  public formatRouteResult(category: string, entry: ISearchResult): IPromise<string> {
    return this.formatResult(category, entry, true);
  }

  private formatResult(category: string, entry: ISearchResult, fromRoute = false): IPromise<string> {
    const config = searchResultFormatterRegistry.get(category);
    if (!config) {
      return this.$q.when('');
    }
    let formatter = config.displayFormatter;

    if (this.providerServiceDelegate.hasDelegate(entry.provider, 'search.resultFormatter')) {
      const providerFormatter: IProviderResultFormatter = this.providerServiceDelegate.getDelegate<IProviderResultFormatter>(entry.provider, 'search.resultFormatter');
      if (providerFormatter[category]) {
        formatter = providerFormatter[category];
      }
    }
    return formatter(entry, fromRoute);
  }
}

export class InfrastructureSearchService {
  constructor(private $q: IQService,
              private providerServiceDelegate: any,
              private searchService: SearchService,
              private urlBuilderService: UrlBuilderService) {}

  public getSearcher(): InfrastructureSearcher {
    return new InfrastructureSearcher(this.$q, this.providerServiceDelegate, this.searchService, this.urlBuilderService);
  }
}

export const INFRASTRUCTURE_SEARCH_SERVICE = 'spinnaker.infrastructure.search.service';
module(INFRASTRUCTURE_SEARCH_SERVICE, [
  SEARCH_SERVICE,
  URL_BUILDER_SERVICE,
  PROVIDER_SERVICE_DELEGATE,
]).service('infrastructureSearchService', InfrastructureSearchService);
