import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { ISearchResultFormatter } from '../search/searchResult/searchResultFormatter.registry';
import { ISearchResult } from '../search/search.service';
import { searchResultFormatterRegistry } from '../search/searchResult/searchResultFormatter.registry';

export interface IApplicationSearchResult extends ISearchResult {
  application: string;
}

export class ApplicationSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Applications'; }
  public get order() { return 1; }
  public get icon() { return 'window-maximize'; }
  public displayFormatter(searchResult: IApplicationSearchResult): IPromise<string> {
    return $q.when(searchResult.application);
  }
}

searchResultFormatterRegistry.register('applications', new ApplicationSearchResultFormatter());
