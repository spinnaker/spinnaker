import { module, IDeferred, IPromise, IQService } from 'angular';
import { Observable, Subject } from 'rxjs';

import { UrlBuilderService, URL_BUILDER_SERVICE } from 'core/navigation';
import { ProviderServiceDelegate, PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider';

import { getFallbackResults, ISearchResult, ISearchResults, SearchService, SEARCH_SERVICE } from '../search.service';
import { IResultDisplayFormatter, ISearchResultType, searchResultTypeRegistry, } from '../searchResult/searchResultsType.registry';
import { externalSearchRegistry } from '../externalSearch.registry';
import { SearchStatus } from '../searchResult/SearchResults';

export interface ISearchResultSet<T extends ISearchResult = any> {
  type: ISearchResultType;
  results: T[];
  status: SearchStatus;
  error?: any;
}

export interface IProviderResultFormatter {
  [category: string]: IResultDisplayFormatter,
}

export class InfrastructureSearcher {
  private deferred: IDeferred<ISearchResultSet[]>;
  public querySubject: Subject<string> = new Subject<string>();

  constructor(private $q: IQService, private providerServiceDelegate: ProviderServiceDelegate, searchService: SearchService, urlBuilderService: UrlBuilderService) {
    this.querySubject.switchMap(
      (query: string) => {
        if (!query || query.trim() === '') {
          return Observable.of(getFallbackResults());
        }
        return Observable.zip(
          searchService.search({ q: query, type: searchResultTypeRegistry.getSearchCategories() }),
          externalSearchRegistry.search({ q: query }).toArray(),
          (s1: ISearchResults<any>, s2: ISearchResultSet[]) => {
            s1.results = s2.reduce((acc, srs: ISearchResultSet) => acc.concat(srs.results), s1.results);
            return s1;
          }
        )
      })
      .subscribe((result: ISearchResults<ISearchResult>) => {
        const categorizedSearchResults: { [type: string]: ISearchResult[] } = result.results.reduce((categories: { [type: string]: ISearchResult[] }, entry: ISearchResult) => {
          this.formatResult(entry.type, entry).then((name) => entry.displayName = name);
          entry.href = urlBuilderService.buildFromMetadata(entry);
          if (!categories[entry.type]) {
            categories[entry.type] = [];
          }
          categories[entry.type].push(entry);
          return categories;
        }, {});

        const searchResults: ISearchResultSet[] = Object.keys(categorizedSearchResults)
          .filter(c => searchResultTypeRegistry.get(c))
          .map(category => {
            const type = searchResultTypeRegistry.get(category);
            const results = categorizedSearchResults[category];
            return { type, results, status: SearchStatus.FINISHED };
          });

        this.deferred.resolve(searchResults);
      });
  }

  public query(q: string): IPromise<ISearchResultSet[]> {
    this.deferred = this.$q.defer();
    this.querySubject.next(q);
    return this.deferred.promise;
  }

  public getCategoryConfig(category: string): ISearchResultType {
    return searchResultTypeRegistry.get(category);
  }

  public formatRouteResult(category: string, entry: ISearchResult): IPromise<string> {
    return this.formatResult(category, entry, true);
  }

  private formatResult(category: string, entry: ISearchResult, fromRoute = false): IPromise<string> {
    const config = searchResultTypeRegistry.get(category);
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
    return this.$q.when(formatter(entry, fromRoute));
  }
}

export class InfrastructureSearchService {
  constructor(private $q: IQService, private providerServiceDelegate: any, private searchService: SearchService, private urlBuilderService: UrlBuilderService) {
  }

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
