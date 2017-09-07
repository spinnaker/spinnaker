import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { IResultRenderer, ISearchResult, ISearchResultFormatter, searchResultFormatterRegistry } from '../search';
import { ApplicationDisplayRenderer } from './ApplicationDisplayRenderer';

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
  public get displayRenderer(): IResultRenderer {
    return ApplicationDisplayRenderer.renderer()
  }
}

searchResultFormatterRegistry.register('applications', new ApplicationSearchResultFormatter());
