import { isEmpty, isObject, isString } from 'lodash';
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
    this.querySubject.switchMap(
      (query: string | IQueryParams) => {

        if (!query || (isString(query) && (query.trim() === '') || (isObject(query) && isEmpty(query)))) {
          return Observable.of(getFallbackResults());
        }

        const searchParams: ISearchParams = {
          type: searchResultFormatterRegistry.getSearchCategories()
        };
        if (isString(query)) {
          searchParams.q = query;
        } else {
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
        const tmp: { [type: string]: ISearchResult[] } = result.results.reduce((categories: { [type: string]: ISearchResult[] }, entry: ISearchResult) => {
          this.formatResult(entry.type, entry).then((name) => entry.displayName = name);
          entry.href = urlBuilderService.buildFromMetadata(entry);
          if (!categories[entry.type]) {
            categories[entry.type] = [];
          }
          categories[entry.type].push(entry);
          return categories;
        }, {});
        this.deferred.resolve(Object.keys(tmp)
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
              results: tmp[category]
            };
          })
        );
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
