import { module, IDeferred, IPromise, IQService } from 'angular';
import { Observable, Subject } from 'rxjs';

import { ProviderServiceDelegate, PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider';

import { InfrastructureSearchServiceV2 } from './infrastructureSearchV2.service';
import { ISearchResult } from '../search.service';
import { SearchResultType, searchResultTypeRegistry } from '../searchResult';
import { SearchStatus } from '../searchResult/SearchResults';

export interface ISearchResultSet<T extends ISearchResult = ISearchResult> {
  type: SearchResultType;
  results: T[];
  status: SearchStatus;
  error?: any;
  query?: string;
}

export interface IProviderResultFormatter {
  [category: string]: (entry: ISearchResult, fromRoute?: boolean) => string;
}

export class InfrastructureSearcher {
  private deferred: IDeferred<ISearchResultSet[]>;
  public querySubject: Subject<string> = new Subject<string>();

  constructor(private $q: IQService, private providerServiceDelegate: ProviderServiceDelegate) {
    this.querySubject
      .switchMap((query: string) => {
        if (!query || query.trim() === '') {
          const fallbackResults = searchResultTypeRegistry
            .getAll()
            .map(type => ({ type, results: [], status: SearchStatus.INITIAL } as ISearchResultSet));
          return Observable.of(fallbackResults);
        }
        return InfrastructureSearchServiceV2.search({ key: query }).toArray();
      })
      .subscribe((result: ISearchResultSet[]) => {
        this.deferred.resolve(result);
      });
  }

  public query(q: string): IPromise<ISearchResultSet[]> {
    this.deferred = this.$q.defer();
    this.querySubject.next(q);
    return this.deferred.promise;
  }

  public getCategoryConfig(category: string): SearchResultType {
    return searchResultTypeRegistry.get(category);
  }

  public formatRouteResult(category: string, entry: ISearchResult): IPromise<string> {
    return this.formatResult(category, entry, true);
  }

  private formatResult(category: string, entry: ISearchResult, fromRoute = false): IPromise<string> {
    const type = searchResultTypeRegistry.get(category);
    if (!type) {
      return this.$q.when('');
    }
    let formatter = type.displayFormatter;

    if (this.providerServiceDelegate.hasDelegate(entry.provider, 'search.resultFormatter')) {
      const providerFormatter: IProviderResultFormatter = this.providerServiceDelegate.getDelegate<
        IProviderResultFormatter
      >(entry.provider, 'search.resultFormatter');
      if (providerFormatter[category]) {
        formatter = providerFormatter[category];
      }
    }
    return this.$q.when(formatter(entry, fromRoute));
  }
}

export class InfrastructureSearchService {
  public static $inject = ['$q', 'providerServiceDelegate'];
  constructor(private $q: IQService, private providerServiceDelegate: any) {
    'ngInject';
  }

  public getSearcher(): InfrastructureSearcher {
    return new InfrastructureSearcher(this.$q, this.providerServiceDelegate);
  }
}

export const INFRASTRUCTURE_SEARCH_SERVICE = 'spinnaker.infrastructure.search.service';
module(INFRASTRUCTURE_SEARCH_SERVICE, [PROVIDER_SERVICE_DELEGATE]).service(
  'infrastructureSearchService',
  InfrastructureSearchService,
);
