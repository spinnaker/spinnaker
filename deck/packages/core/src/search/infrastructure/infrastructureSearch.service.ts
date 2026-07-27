import { of as observableOf, Subject } from 'rxjs';
import { switchMap, toArray } from 'rxjs/operators';

import type { ProviderServiceDelegate } from '../../cloudProvider/providerService.delegate';
import { InfrastructureSearchServiceV2 } from './infrastructureSearchV2.service';
import type { ISearchResult } from '../search.service';
import { SearchStatus } from '../searchResult/SearchStatus';
import type { SearchResultType } from '../searchResult/searchResultType';
import { searchResultTypeRegistry } from '../searchResult/searchResultType.registry';
import type { Deferred } from '../../utils/deferred';
import { createDeferred } from '../../utils/deferred';

export interface ISearchResultSet<T extends ISearchResult = ISearchResult> {
  type: SearchResultType;
  results: T[];
  status: SearchStatus;
  error?: any;
  query?: string;
}

export type ISearchResultFormatter = (entry: ISearchResult, fromRoute?: boolean) => string | PromiseLike<string>;
export interface IProviderResultFormatter {
  [category: string]: ISearchResultFormatter;
}

export class InfrastructureSearcher {
  private deferred: Deferred<ISearchResultSet[]>;
  public querySubject: Subject<string> = new Subject<string>();

  constructor(private providerServiceDelegate: ProviderServiceDelegate) {
    this.querySubject
      .pipe(
        switchMap((query: string) => {
          if (!query || query.trim() === '') {
            const fallbackResults = searchResultTypeRegistry
              .getAll()
              .map((type) => ({ type, results: [], status: SearchStatus.INITIAL } as ISearchResultSet));
            return observableOf(fallbackResults);
          }
          return InfrastructureSearchServiceV2.search({ key: query }).pipe(toArray());
        }),
      )
      .subscribe((result: ISearchResultSet[]) => {
        this.deferred.resolve(result);
      });
  }

  public query(q: string): Promise<ISearchResultSet[]> {
    this.deferred = createDeferred<ISearchResultSet[]>();
    this.querySubject.next(q);
    return this.deferred.promise;
  }

  public getCategoryConfig(category: string): SearchResultType {
    return searchResultTypeRegistry.get(category);
  }

  public formatRouteResult(category: string, entry: ISearchResult): Promise<string> {
    return this.formatResult(category, entry, true);
  }

  private formatResult(category: string, entry: ISearchResult, fromRoute = false): Promise<string> {
    const type = searchResultTypeRegistry.get(category);
    if (!type) {
      return Promise.resolve('');
    }
    let formatter: ISearchResultFormatter = type.displayFormatter;

    if (this.providerServiceDelegate.hasDelegate(entry.provider, 'search.resultFormatter')) {
      const providerFormatter: IProviderResultFormatter = this.providerServiceDelegate.getDelegate<
        IProviderResultFormatter
      >(entry.provider, 'search.resultFormatter');
      if (providerFormatter[category]) {
        formatter = providerFormatter[category];
      }
    }
    return Promise.resolve(formatter(entry, fromRoute));
  }
}

export class InfrastructureSearchService {
  constructor(private providerServiceDelegate: any) {}

  public getSearcher(): InfrastructureSearcher {
    return new InfrastructureSearcher(this.providerServiceDelegate);
  }
}
