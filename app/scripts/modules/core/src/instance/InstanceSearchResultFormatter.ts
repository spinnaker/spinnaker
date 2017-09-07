import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { IResultRenderer, ISearchResult, ISearchResultFormatter, searchResultFormatterRegistry } from '../search';
import { InstanceDisplayRenderer } from './InstanceDisplayRenderer';

export interface IInstanceSearchResult extends ISearchResult {
  serverGroup?: string;
  instanceId: string;
  region: string;
}

export class InstanceSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Instances'; }
  public get order() { return 4; }
  public get icon() { return 'hdd-o'; }
  public displayFormatter(searchResult: IInstanceSearchResult): IPromise<string> {
    const serverGroup = searchResult.serverGroup || 'standalone instance';
    return $q.when(searchResult.instanceId + ' (' + serverGroup + ' - ' + searchResult.region + ')');
  }
  public get displayRenderer(): IResultRenderer {
    return InstanceDisplayRenderer.renderer()
  }
}

searchResultFormatterRegistry.register('instances', new InstanceSearchResultFormatter());
