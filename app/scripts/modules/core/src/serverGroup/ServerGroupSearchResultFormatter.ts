import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { IResultRenderer, ISearchResult, ISearchResultFormatter, searchResultFormatterRegistry } from '../search';
import { ServerGroupDisplayRenderer } from './ServerGroupDisplayRenderer';

export interface IServerGroupSearchResult extends ISearchResult {
  serverGroup: string;
  region: string;
}

export class ServerGroupSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Server Groups'; }
  public get order() { return 6; }
  public get icon() { return 'th-large'; }
  public displayFormatter(searchResult: IServerGroupSearchResult): IPromise<string> {
    return $q.when(searchResult.serverGroup + ' (' + searchResult.region + ')');
  }
  public get displayRenderer(): IResultRenderer {
    return ServerGroupDisplayRenderer.renderer()
  }
}

searchResultFormatterRegistry.register('serverGroups', new ServerGroupSearchResultFormatter());
